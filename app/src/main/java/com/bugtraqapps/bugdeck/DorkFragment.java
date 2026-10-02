package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bugtraqapps.bugdeck.core.Fetcher;
import com.bugtraqapps.bugdeck.dork.DorkProvider;
import com.bugtraqapps.bugdeck.dork.DorkProviders;
import com.bugtraqapps.bugdeck.dork.DorkSearch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dork tab: run a search expression against a provider and list the URLs.
 *
 * <p>Provider ids come from {@link DorkProviders#available()} rather than a
 * hardcoded array, so a provider added to the core shows up here automatically
 * and the two can never drift.
 */
public final class DorkFragment extends Fragment {

    private TextInputEditText queryInput;
    private TextInputEditText domainInput;
    private Spinner providerSpinner;
    private TextInputEditText keyInput;
    private TextInputEditText cxInput;
    private View keyRow;
    private View keyLayout;
    private View cxLayout;
    private Slider pagesSeek;
    private TextView pagesValue;
    private MaterialButton searchBtn;
    private MaterialButton clearBtn;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView resultsRecycler;

    private DorkResultAdapter resultAdapter;
    private final List<String> resultUrls = new ArrayList<>();
    private final List<DorkProvider> providers = new ArrayList<>();

    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        resultAdapter = new DorkResultAdapter(resultUrls);
        providers.addAll(DorkProviders.available());
        SettingsDialog.applyToCore(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dork, container, false);

        queryInput = view.findViewById(R.id.dork_query);
        domainInput = view.findViewById(R.id.dork_domain);
        providerSpinner = view.findViewById(R.id.dork_provider);
        keyInput = view.findViewById(R.id.dork_key);
        keyRow = view.findViewById(R.id.dorkKeyRow);
        keyLayout = view.findViewById(R.id.dorkKeyLayout);
        cxInput = view.findViewById(R.id.dork_cx);
        cxLayout = view.findViewById(R.id.dorkCxLayout);
        pagesSeek = view.findViewById(R.id.dork_pages);
        pagesValue = view.findViewById(R.id.dork_pages_value);
        searchBtn = view.findViewById(R.id.dork_search);
        clearBtn = view.findViewById(R.id.dork_clear);
        progressBar = view.findViewById(R.id.dork_progress);
        statusText = view.findViewById(R.id.dork_status);
        resultsRecycler = view.findViewById(R.id.dork_results);

        resultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultsRecycler.setAdapter(resultAdapter);

        List<String> labels = new ArrayList<>();
        for (DorkProvider p : providers) {
            labels.add(p.title() + (p.requiresKey() ? "  (key)" : ""));
        }
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, labels);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);

        pagesSeek.addOnChangeListener((slider, value, fromUser) -> {
            int p = Math.round(value);
            pagesValue.setText(getString(R.string.pages_fmt, p));
        });
        pagesValue.setText(getString(R.string.pages_fmt, Math.round(pagesSeek.getValue())));

        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                DorkProvider p = providers.get(position);
                boolean needsKey = p.requiresKey();
                keyRow.setVisibility(needsKey ? View.VISIBLE : View.GONE);
                boolean needsCx = p instanceof DorkProviders.GoogleCse;
                cxLayout.setVisibility(needsCx ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        searchBtn.setOnClickListener(v -> startSearch());
        clearBtn.setOnClickListener(v -> clearResults());

        return view;
    }

    private void startSearch() {
        final DorkProvider provider = providers.get(providerSpinner.getSelectedItemPosition());
        final String query  = text(queryInput);
        final String domain = text(domainInput);
        final int pages     = Math.max(1, Math.round(pagesSeek.getValue()));

        if (query.isEmpty() && domain.isEmpty()) {
            Toast.makeText(requireContext(), R.string.dork_need_query, Toast.LENGTH_SHORT).show();
            return;
        }

        // Prefer the stored credential; let the inline field override it.
        final String key = text(keyInput).isEmpty() ? storedKeyFor(provider) : text(keyInput);

        // Google needs the cx both for its URL and for the core's guard clause.
        if (provider instanceof DorkProviders.GoogleCse) {
            String cx = text(cxInput);
            if (cx.isEmpty()) cx = SettingsDialog.cseId(requireContext());
            if (cx.isEmpty()) {
                Toast.makeText(requireContext(), R.string.dork_need_cx, Toast.LENGTH_LONG).show();
                return;
            }
            DorkProviders.CseIdHolder.cx = cx;
        }

        searchBtn.setEnabled(false);
        clearBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        statusText.setText(R.string.dork_searching);

        resultUrls.clear();
        resultAdapter.notifyDataSetChanged();

        final int timeout = SettingsDialog.timeoutMs(requireContext());
        final String ua = SettingsDialog.userAgent(requireContext());

        executor.execute(() -> {
            try {
                final DorkSearch.Result r =
                    new DorkSearch(ua, timeout)
                        .searchAllPages(provider, query, domain, key, pages, null);

                mainHandler.post(() -> {
                    finish();
                    if (r.failed()) {
                        statusText.setText(getString(R.string.dork_failed,
                                r.note != null ? r.note : "http " + r.httpStatus));
                        return;
                    }
                    resultUrls.addAll(r.urls);
                    resultAdapter.notifyDataSetChanged();
                    statusText.setText(getString(R.string.dork_found, r.urls.size()));
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    finish();
                    statusText.setText(getString(R.string.dork_failed, String.valueOf(e.getMessage())));
                });
            }
        });
    }

    private String storedKeyFor(DorkProvider p) {
        String id = p.id();
        if ("brave".equals(id))  return SettingsDialog.braveKey(requireContext());
        if ("google".equals(id)) return SettingsDialog.googleKey(requireContext());
        return "";
    }

    private static String text(TextInputEditText f) {
        CharSequence c = f.getText();
        return c == null ? "" : c.toString().trim();
    }

    private void finish() {
        searchBtn.setEnabled(true);
        clearBtn.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    private void clearResults() {
        resultUrls.clear();
        resultAdapter.notifyDataSetChanged();
        statusText.setText("");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ---- adapter --------------------------------------------------------

    static final class DorkResultAdapter extends RecyclerView.Adapter<DorkResultAdapter.VH> {
        private final List<String> data;

        DorkResultAdapter(List<String> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dork_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final String url = data.get(position);
            holder.url.setText(url);
            holder.itemView.setOnClickListener(view -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("BugDeck", url));
                    Toast.makeText(view.getContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override public int getItemCount() { return data.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView url;
            VH(View v) {
                super(v);
                url = v.findViewById(R.id.dork_result_url);
            }
        }
    }
}
