package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FridaFragment extends Fragment {

    private TextInputEditText serverInput;
    private TextInputEditText tokenInput;
    private ProgressBar progressBar;
    private TextView statusText;
    private WebView fridaWebView;
    private MaterialButton connectBtn;
    private MaterialButton disconnectBtn;
    private MaterialCardView connectCard;

    private UnifiedClient client;
    private String savedHost = "";
    private String savedToken = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        // Connection card
        connectCard = new MaterialCardView(requireContext());
        connectCard.setRadius(12);
        connectCard.setCardElevation(4);
        LinearLayout cardLayout = new LinearLayout(requireContext());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(16, 16, 16, 16);

        TextView title = new TextView(requireContext());
        title.setText("Frida Control");
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        cardLayout.addView(title);

        TextInputLayout serverLayout = new TextInputLayout(requireContext());
        serverLayout.setHint("bugXhunter server URL");
        serverInput = new TextInputEditText(requireContext());
        serverInput.setHint("http://10.0.0.1:7080");
        serverLayout.addView(serverInput);
        cardLayout.addView(serverLayout);

        TextInputLayout tokenLayout = new TextInputLayout(requireContext());
        tokenLayout.setHint("Auth token");
        tokenInput = new TextInputEditText(requireContext());
        tokenInput.setHint("from bugXhunter config.json");
        tokenLayout.addView(tokenInput);
        cardLayout.addView(tokenLayout);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        connectBtn = new MaterialButton(requireContext());
        connectBtn.setText("Connect");
        connectBtn.setOnClickListener(v -> connect());
        buttonRow.addView(connectBtn);

        disconnectBtn = new MaterialButton(requireContext());
        disconnectBtn.setText("Disconnect");
        disconnectBtn.setOnClickListener(v -> disconnect());
        disconnectBtn.setEnabled(false);
        buttonRow.addView(disconnectBtn);

        cardLayout.addView(buttonRow);

        statusText = new TextView(requireContext());
        statusText.setText("Disconnected");
        statusText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        cardLayout.addView(statusText);

        connectCard.addView(cardLayout);
        root.addView(connectCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Progress bar
        progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // WebView for Frida UI
        fridaWebView = new WebView(requireContext());
        fridaWebView.getSettings().setJavaScriptEnabled(true);
        fridaWebView.getSettings().setDomStorageEnabled(true);
        fridaWebView.getSettings().setAllowFileAccess(true);
        fridaWebView.getSettings().setAllowContentAccess(true);
        fridaWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                statusText.setText("Connected to " + savedHost);
                disconnectBtn.setEnabled(true);
            }
        });
        root.addView(fridaWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        return root;
    }

    private void connect() {
        savedHost = serverInput.getText() != null ? serverInput.getText().toString().trim() : "";
        savedToken = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";

        if (savedHost.isEmpty()) {
            statusText.setText("Error: server URL required");
            return;
        }

        // Ensure URL has scheme
        if (!savedHost.startsWith("http://") && !savedHost.startsWith("https://")) {
            savedHost = "http://" + savedHost;
        }

        // Load the Frida web UI from the server
        String fridaUrl = savedHost + "/frida/page?token=" + savedToken;
        fridaWebView.loadUrl(fridaUrl);
        connectBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Connecting...");
    }

    private void disconnect() {
        fridaWebView.loadUrl("about:blank");
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        statusText.setText("Disconnected");
        savedHost = "";
        savedToken = "";
    }

    public void onDestroyView() {
        if (fridaWebView != null) {
            fridaWebView.destroy();
            fridaWebView = null;
        }
    }
}
