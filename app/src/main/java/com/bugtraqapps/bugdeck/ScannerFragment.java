package com.bugtraqapps.bugdeck;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bugtraqapps.bugdeck.core.AdminFinder;
import com.bugtraqapps.bugdeck.core.Crawler;
import com.bugtraqapps.bugdeck.core.Fetcher;
import com.bugtraqapps.bugdeck.core.SqlInjector;
import com.bugtraqapps.bugdeck.core.Verdict;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scanner tab: SQL injection probe and admin panel finder.
 *
 * <p>Every row carries a {@link Verdict} from the core, including BLOCKED and
 * SKIPPED. Nothing here collapses those into "not vulnerable" — that was the
 * defect that made the original apps report WAF blocks and dead hosts as clean.
 */
public final class ScannerFragment extends Fragment {

    private static final String[] WORDLIST_FILES = {
        "mix1", "mix2", "php", "asp", "brf", "cfm", "cgi", "js"
    };

    private static final int DEFAULT_THREADS = 8;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_PAGES = 50;

    private TextInputEditText targetInput;
    private android.widget.Spinner wordlistSpinner;
    private MaterialButtonToggleGroup modeGroup;
    private View wordlistRow;
    private android.widget.CheckBox crawlBox;
    private View depthRow;
    private TextInputLayout depthLayout;
    private TextInputEditText depthInput;
    private android.widget.Button scanBtn;
    private android.widget.Button stopBtn;
    private android.widget.ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView resultsRecycler;

    private ResultAdapter resultAdapter;
    private final List<ScanResult> results = new ArrayList<>();

    private ExecutorService executor;
    private Handler mainHandler;
    private volatile boolean cancelRequested = false;
    private volatile int done = 0;
    private volatile int total = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        resultAdapter = new ResultAdapter(results);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scanner, container, false);

        targetInput = v.findViewById(R.id.target);
        wordlistSpinner = v.findViewById(R.id.wordlistSpinner);
        wordlistRow = v.findViewById(R.id.wordlistRow);
        modeGroup = v.findViewById(R.id.modeGroup);
        crawlBox = v.findViewById(R.id.crawlBox);
        depthInput = v.findViewById(R.id.depth);
        depthLayout = v.findViewById(R.id.depthLayout);
        depthRow = v.findViewById(R.id.depthLabel);
        scanBtn = v.findViewById(R.id.scan);
        stopBtn = v.findViewById(R.id.stop);
        progressBar = v.findViewById(R.id.progress);
        statusText = v.findViewById(R.id.status);
        resultsRecycler = v.findViewById(R.id.results);

        resultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultsRecycler.setAdapter(resultAdapter);

        ArrayAdapter<String> wl = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, WORDLIST_FILES);
        wordlistSpinner.setAdapter(wl);

        // The wordlist picker only applies to the admin finder.
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean admin = checkedId == R.id.modeAdmin;
            wordlistRow.setVisibility(admin ? View.VISIBLE : View.GONE);
            setCrawlVisible(!admin);
        });
        modeGroup.check(R.id.modeSqli);
        setCrawlVisible(true);

        scanBtn.setOnClickListener(x -> startScan());
        stopBtn.setOnClickListener(x -> {
            cancelRequested = true;
            statusText.setText(R.string.stopping);
        });

        return v;
    }

    private void setCrawlVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        crawlBox.setVisibility(vis);
        depthRow.setVisibility(vis);
        depthLayout.setVisibility(vis);
    }

    // ---- scanning -------------------------------------------------------

    private void startScan() {
        final String target = text(targetInput);
        if (target.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), R.string.need_target,
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean adminMode = modeGroup.getCheckedButtonId() == R.id.modeAdmin;
        final String wordlist = String.valueOf(wordlistSpinner.getSelectedItem());
        final boolean doCrawl = crawlBox.isChecked();
        final int depth = clampDepth(text(depthInput));
        final int timeout = SettingsDialog.timeoutMs(requireContext());
        final String ua = SettingsDialog.userAgent(requireContext());

        cancelRequested = false;
        done = 0;
        total = 0;
        results.clear();
        resultAdapter.notifyDataSetChanged();

        setScanning(true);
        progressBar.setIndeterminate(true);
        statusText.setText(R.string.starting);

        executor.execute(() -> {
            try {
                if (adminMode) {
                    runAdmin(target, wordlist, timeout, ua);
                } else {
                    List<String> targets = new ArrayList<>();
                    targets.add(target);
                    if (doCrawl) {
                        Crawler.Result c = new Crawler(target, ua, timeout)
                            .crawl(depth, MAX_PAGES, true);
                        if (c.failure != null) {
                            postError(c.failure);
                            return;
                        }
                        postStatus(getString(R.string.crawled, c.urls.size(), c.pagesFetched));
                        targets.addAll(c.urls);
                    }
                    runSqli(targets, timeout, ua);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                postError(String.valueOf(e.getMessage()));
            } finally {
                mainHandler.post(this::finishScan);
            }
        });
    }

    private void runSqli(List<String> urls, int timeout, String ua) {
        total = urls.size();
        final int t = total;
        mainHandler.post(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setMax(Math.max(1, t));
        });

        SqlInjector injector = new SqlInjector(ua, timeout);
        for (String url : urls) {
            if (cancelRequested) return;
            SqlInjector.Result r = injector.probe(url);
            if (cancelRequested) return;
            publish(new ScanResult(url, r.verdict, r.evidence, r.probeStatus, r.baselineStatus));
        }
    }

    private void runAdmin(String base, String wordlist, int timeout, String ua)
            throws InterruptedException {
        List<String> paths;
        try {
            paths = loadWordlist(wordlist);
        } catch (IOException e) {
            postError(getString(R.string.wordlist_missing, wordlist));
            return;
        }
        if (paths.isEmpty()) {
            postError(getString(R.string.wordlist_empty, wordlist));
            return;
        }

        total = paths.size();
        final int t = total;
        mainHandler.post(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setMax(Math.max(1, t));
        });

        AdminFinder finder = new AdminFinder(ua, timeout, DEFAULT_THREADS, 0);
        finder.setFollowRedirects(false);
        finder.scan(base, paths, (d, ignored, hit) -> {
            if (cancelRequested) return;
            publish(new ScanResult(hit.url, hit.verdict, hit.note, hit.status, 0));
        });
    }

    private void publish(final ScanResult r) {
        done++;
        mainHandler.post(() -> {
            results.add(r);
            resultAdapter.notifyItemInserted(results.size() - 1);
            progressBar.setProgress(done);
            statusText.setText(getString(R.string.progress_fmt, done, total));
            if (!results.isEmpty()) {
                resultsRecycler.scrollToPosition(results.size() - 1);
            }
        });
    }

    private void postStatus(final String s) {
        mainHandler.post(() -> statusText.setText(s));
    }

    private void postError(final String s) {
        mainHandler.post(() -> statusText.setText(getString(R.string.scan_error, s)));
    }

    private void setScanning(boolean on) {
        scanBtn.setEnabled(!on);
        stopBtn.setEnabled(on);
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void finishScan() {
        setScanning(false);
        long vuln = 0;
        for (ScanResult r : results) {
            if (r.verdict == Verdict.VULNERABLE) vuln++;
        }
        statusText.setText(getString(R.string.done_fmt, results.size(), vuln));
    }

    private List<String> loadWordlist(String name) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream is = requireContext().getAssets().open("wordlists/" + name + ".txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
            }
        }
        return lines;
    }

    private static String text(TextInputEditText f) {
        CharSequence c = f.getText();
        return c == null ? "" : c.toString().trim();
    }

    private static int clampDepth(String s) {
        int d;
        try {
            d = Integer.parseInt(s);
        } catch (Exception e) {
            d = 1;
        }
        if (d < 1) d = 1;
        if (d > MAX_DEPTH) d = MAX_DEPTH;
        return d;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelRequested = true;
        executor.shutdownNow();
    }

    // ---- row model ------------------------------------------------------

    static final class ScanResult {
        final String url;
        final Verdict verdict;
        final String evidence;
        final int probeStatus;
        final int baselineStatus;

        ScanResult(String url, Verdict verdict, String evidence, int probeStatus, int baselineStatus) {
            this.url = url;
            this.verdict = verdict;
            this.evidence = evidence == null ? "" : evidence;
            this.probeStatus = probeStatus;
            this.baselineStatus = baselineStatus;
        }
    }

    static final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<ScanResult> data;

        ResultAdapter(List<ScanResult> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final ScanResult r = data.get(position);

            holder.url.setText(r.url);
            // Labels come from resources, not literals, so the verdict names are
            // localizable like everything else.
            holder.verdict.setText(holder.itemView.getContext().getString(labelFor(r.verdict)));
            // The chip style carries the colour; a ContextCompat lookup keeps this
            // working on API 21, where Context.getColor(int) does not exist yet.
            android.content.Context ctx = holder.itemView.getContext();
            holder.verdict.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(ctx, bgFor(r.verdict))));
            holder.verdict.setTextColor(ContextCompat.getColor(ctx, fgFor(r.verdict)));

            if (r.evidence.isEmpty()) {
                holder.evidence.setVisibility(View.GONE);
            } else {
                holder.evidence.setVisibility(View.VISIBLE);
                holder.evidence.setText(r.evidence);
            }

            StringBuilder st = new StringBuilder();
            st.append(holder.itemView.getContext().getString(R.string.status_fmt, r.probeStatus));
            if (r.baselineStatus > 0) {
                st.append(holder.itemView.getContext()
                    .getString(R.string.status_base_fmt, r.baselineStatus));
            }
            holder.status.setText(st.toString());

            holder.itemView.setOnClickListener(v2 -> copy(holder.itemView.getContext(), r.url));
        }

        private static void copy(android.content.Context c, String s) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                c.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(android.content.ClipData.newPlainText("BugDeck", s));
            android.widget.Toast.makeText(c, R.string.copied, android.widget.Toast.LENGTH_SHORT).show();
        }

        private static int labelFor(Verdict v) {
            switch (v) {
                case VULNERABLE:     return R.string.v_vulnerable;
                case NOT_VULNERABLE: return R.string.v_not_vulnerable;
                case BLOCKED:        return R.string.v_blocked;
                case SKIPPED:        return R.string.v_skipped;
                default:             return R.string.v_error;
            }
        }

        private static int bgFor(Verdict v) {
            switch (v) {
                case VULNERABLE:     return R.color.verdict_vulnerable_bg;
                case NOT_VULNERABLE: return R.color.verdict_clean_bg;
                case BLOCKED:        return R.color.verdict_blocked_bg;
                default:             return R.color.verdict_skipped_bg;
            }
        }

        private static int fgFor(Verdict v) {
            switch (v) {
                case VULNERABLE:     return R.color.verdict_vulnerable;
                case NOT_VULNERABLE: return R.color.verdict_clean;
                case BLOCKED:        return R.color.verdict_blocked;
                default:             return R.color.verdict_skipped;
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView url;
            final Chip verdict;
            final TextView evidence;
            final TextView status;

            VH(View v) {
                super(v);
                url = v.findViewById(R.id.result_url);
                verdict = v.findViewById(R.id.result_verdict);
                evidence = v.findViewById(R.id.result_evidence);
                status = v.findViewById(R.id.result_status);
            }
        }
    }
}
