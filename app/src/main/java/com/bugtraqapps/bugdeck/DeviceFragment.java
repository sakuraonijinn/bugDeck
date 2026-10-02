package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Device tab: the features that genuinely need shell identity, and nothing else.
 *
 * <p>Two capabilities, both of which are impossible with the ordinary SDK:
 * <ul>
 *   <li><b>Logcat grep.</b> READ_LOGS is signature|privileged, so a normal app
 *       cannot read the system log. This pairs with the {@code log4j} payload
 *       category, which is exactly a list of root-shell paths to probe.</li>
 *   <li><b>Installed-package enumeration.</b> Sees packages the normal
 *       PackageManager hides, which is how you spot tooling that hides itself.</li>
 * </ul>
 *
 * <p>Everything degrades cleanly: with no Shizuku the tab explains what is
 * missing and the other three tabs are unaffected.
 */
public final class DeviceFragment extends Fragment {

    /** Root/analyst tooling worth surfacing when it is present. */
    private static final String[] NOTABLE = {
        "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su",
        "com.koushikdutta.superuser", "com.thirdparty.superuser",
        "com.yellowes.su", "com.proo.torsocks", "org.creeplays.hack",
        "com.zhiliaoapp.mumu", "com.guoshi.httpcanary", "com.guoshi.httpcanary.pro",
        "com.eg.android.AlipayGphone", "com.tencent.mm", "com.whatsapp",
        "com.termux", "com.termux.api", "net.dinglisch.android.taskerm"
    };

    private TextView statusLabel;
    private TextView detailLabel;
    private TextView outputView;
    private TextView outputStatus;
    private MaterialButton grantBtn;
    private TextInputEditText filterInput;

    private ExecutorService executor;
    private Handler mainHandler;
    private volatile boolean forceKilled = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_device, container, false);

        statusLabel = v.findViewById(R.id.dev_status);
        detailLabel = v.findViewById(R.id.dev_detail);
        outputView = v.findViewById(R.id.dev_output);
        outputStatus = v.findViewById(R.id.dev_output_status);
        grantBtn = v.findViewById(R.id.dev_grant);
        filterInput = v.findViewById(R.id.dev_filter);

        v.findViewById(R.id.dev_refresh).setOnClickListener(x -> refresh());
        grantBtn.setOnClickListener(x -> {
            ShizukuState.requestPermission();
            refresh();
        });
        v.<MaterialButton>findViewById(R.id.dev_logcat)
            .setOnClickListener(x -> readLogcat(text(filterInput)));
        v.<MaterialButton>findViewById(R.id.dev_apps)
            .setOnClickListener(x -> listPackages());

        refresh();
        return v;
    }

    private void refresh() {
        ShizukuState.Status s = ShizukuState.status();
        statusLabel.setText(getString(R.string.shizuku_title) + ": " + s.name());
        detailLabel.setText(ShizukuState.explain(requireContext(), s));

        boolean ready = s == ShizukuState.Status.READY;
        grantBtn.setVisibility(s == ShizukuState.Status.NEEDS_PERMISSION
                ? View.VISIBLE : View.GONE);
        bindEnabled(R.id.dev_logcat, ready);
        bindEnabled(R.id.dev_apps, ready);

        if (!ready) outputView.setText("");
    }

    /** Enable/disable a button by id without holding another field. */
    private void bindEnabled(int id, boolean enabled) {
        View v = getView();
        if (v != null) v.findViewById(id).setEnabled(enabled);
    }

    /**
     * Read logcat and keep only lines matching the filter.
     *
     * <p>This is the path the log4j payload category is built for: the payloads
     * name root-shell paths, and these are the log lines that would show one
     * being touched.
     */
    private void readLogcat(final String filter) {
        if (!ShizukuState.isReady()) {
            Toast.makeText(requireContext(), R.string.shizuku_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        outputStatus.setText(R.string.working);
        outputView.setText("");

        executor.execute(() -> {
            // -d drops the historical buffer and follows the log, so this
            // terminates; -t 200 bounds it anyway.
            String[] out = ShizukuState.newProcess(new String[] {
                "logcat", "-d", "-t", "2000", "-v", "brief"
            });

            String body;
            if (out == null) {
                body = getString(R.string.shizuku_error);
            } else {
                body = filterLines(out[0], filter);
                if (body.isEmpty() && out[1] != null && !out[1].trim().isEmpty()) {
                    body = getString(R.string.shizuku_stderr, out[1].trim());
                }
            }
            final String finalBody = body;
            mainHandler.post(() -> {
                if (forceKilled) return;
                outputView.setText(finalBody.isEmpty()
                        ? getString(R.string.no_matches, filter) : finalBody);
                outputStatus.setText(getString(R.string.logcat_done,
                        finalBody.isEmpty() ? 0 : finalBody.split("\n").length));
            });
        });
    }

    /** Case-insensitive substring match, one per line. */
    private String filterLines(String text, String needle) {
        if (text == null || text.isEmpty()) return "";
        if (needle == null || needle.trim().isEmpty()) return text;
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.toLowerCase(Locale.ROOT).contains(lowerNeedle)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** Enumerate every package, then flag the notable ones. */
    private void listPackages() {
        if (!ShizukuState.isReady()) {
            Toast.makeText(requireContext(), R.string.shizuku_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        outputStatus.setText(R.string.working);
        outputView.setText("");

        executor.execute(() -> {
            String[] out = ShizukuState.newProcess(new String[] {
                "pm", "list", "packages", "-3"
            });
            String body;
            if (out == null || out[0] == null || out[0].trim().isEmpty()) {
                body = getString(R.string.shizuku_error);
            } else {
                List<String> found = new ArrayList<>();
                for (String pkg : NOTABLE) {
                    if (out[0].contains("package:" + pkg)) found.add(pkg);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.packages_count, countPackages(out[0])));
                sb.append('\n');
                if (found.isEmpty()) {
                    sb.append(getString(R.string.packages_none_notable));
                } else {
                    sb.append(getString(R.string.packages_notable)).append('\n');
                    for (String f : found) sb.append("  ").append(f).append('\n');
                }
                body = sb.toString();
            }
            final String finalBody = body;
            mainHandler.post(() -> {
                if (forceKilled) return;
                outputView.setText(finalBody);
                outputStatus.setText("");
            });
        });
    }

    private int countPackages(String listing) {
        int n = 0;
        for (String line : listing.split("\n")) {
            if (line.startsWith("package:")) n++;
        }
        return n;
    }

    private static String text(TextInputEditText f) {
        CharSequence c = f.getText();
        return c == null ? "" : c.toString().trim();
    }

    @Override
    public void onResume() {
        super.onResume();
        forceKilled = false;
        refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        forceKilled = true;
        executor.shutdownNow();
    }
}
