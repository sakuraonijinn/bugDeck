package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Security posture tab.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Local</b> — runs {@link SecurityCheck} on this device.</li>
 *   <li><b>Remote</b> — talks to a linked bugXhunter server and runs the
 *       same checks on an enrolled target device.</li>
 * </ul>
 *
 * <p>Remote mode needs a linked server with a working tunnel. If the server
 * is unlinked or unreachable, only local mode is available.
 */
public class DeviceFragment extends Fragment {

    private TextInputEditText serverInput;
    private TextInputEditText tokenInput;
    private TextView linkStatus;
    private Button scanLocalBtn;
    private Button scanRemoteBtn;
    private LinearLayout findingsList;
    private View remotePanel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        serverInput = view.findViewById(R.id.deviceServer);
        tokenInput   = view.findViewById(R.id.deviceToken);
        linkStatus   = view.findViewById(R.id.deviceLinkStatus);
        scanLocalBtn = view.findViewById(R.id.scanLocal);
        scanRemoteBtn = view.findViewById(R.id.scanRemote);
        findingsList = view.findViewById(R.id.deviceFindings);
        remotePanel  = view.findViewById(R.id.deviceRemotePanel);

        String saved = SettingsDialog.unifiedHost(requireContext());
        String tok   = SettingsDialog.unifiedToken(requireContext());
        if (!saved.isEmpty()) serverInput.setText(saved);
        if (!tok.isEmpty())   tokenInput.setText(tok);

        scanLocalBtn.setOnClickListener(v -> runLocalScan());
        scanRemoteBtn.setOnClickListener(v -> runRemoteScan());

        updateLinkStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLinkStatus();
    }

    private void updateLinkStatus() {
        String host = SettingsDialog.unifiedHost(requireContext());
        String token = SettingsDialog.unifiedToken(requireContext());
        if (!host.isEmpty() && !token.isEmpty()) {
            linkStatus.setText(getString(R.string.device_linked, host));
            scanRemoteBtn.setEnabled(true);
            remotePanel.setVisibility(View.VISIBLE);
        } else {
            linkStatus.setText(R.string.device_unlinked);
            scanRemoteBtn.setEnabled(false);
            remotePanel.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------
    // Local scan
    // ------------------------------------------------------------------

    private void runLocalScan() {
        scanLocalBtn.setEnabled(false);
        findingsList.removeAllViews();
        appendStatus(R.string.device_scanning);

        executor.execute(() -> {
            List<SecurityCheck.Finding> findings = SecurityCheck.run(requireContext());
            main.post(() -> {
                findingsList.removeAllViews();
                for (SecurityCheck.Finding f : findings) {
                    findingsList.addView(findingRow(f));
                }
                scanLocalBtn.setEnabled(true);
            });
        });
    }

    // ------------------------------------------------------------------
    // Remote scan
    // ------------------------------------------------------------------

    private void runRemoteScan() {
        String host = SettingsDialog.unifiedHost(requireContext());
        String token = SettingsDialog.unifiedToken(requireContext());
        if (host.isEmpty() || token.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), R.string.device_link_first,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        scanRemoteBtn.setEnabled(false);
        findingsList.removeAllViews();
        appendStatus(R.string.device_scanning_remote);

        executor.execute(() -> {
            try {
                UnifiedClient client = new UnifiedClient(host, token,
                        SettingsDialog.timeoutMs(requireContext()));
                List<SecurityCheck.Finding> findings =
                        client.remoteDiagnose(requireContext());
                main.post(() -> {
                    findingsList.removeAllViews();
                    if (findings.isEmpty()) {
                        appendStatus(R.string.device_remote_no_results);
                    } else {
                        for (SecurityCheck.Finding f : findings) {
                            findingsList.addView(findingRow(f));
                        }
                    }
                    scanRemoteBtn.setEnabled(true);
                });
            } catch (Exception e) {
                main.post(() -> {
                    findingsList.removeAllViews();
                    appendError(String.valueOf(e.getMessage()));
                    scanRemoteBtn.setEnabled(true);
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private void appendStatus(int resId) {
        TextView tv = new TextView(requireContext());
        tv.setText(resId);
        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        findingsList.addView(tv);
    }

    private void appendError(String msg) {
        TextView tv = new TextView(requireContext());
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.verdict_vulnerable));
        tv.setText(getString(R.string.device_scan_error, msg));
        findingsList.addView(tv);
    }

    private View findingRow(SecurityCheck.Finding f) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(1f);
        card.setRadius(8f);
        card.setStrokeWidth(1);
        card.setStrokeColor(colorFor(f.level));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setPadding(16, 16, 16, 16);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);

        TextView header = new TextView(requireContext());
        header.setText(String.format(Locale.US, "[%s] %s",
                f.level, f.category));
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        header.setTextColor(ContextCompat.getColor(requireContext(), colorFor(f.level)));
        row.addView(header);

        TextView detail = new TextView(requireContext());
        detail.setText(f.detail);
        detail.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        row.addView(detail);

        card.addView(row);
        return card;
    }

    private int colorFor(SecurityCheck.Finding.Level lvl) {
        switch (lvl) {
            case CRITICAL: return R.color.verdict_vulnerable;
            case HIGH:     return R.color.verdict_vulnerable;
            case MEDIUM:   return R.color.verdict_blocked;
            case LOW:      return R.color.verdict_skipped;
            default:       return R.color.verdict_clean;
        }
    }
}
