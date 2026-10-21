package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Frida control: a script console, quick scripts, device and process
 * pickers, and the server's own web UI.
 *
 * <p>Every request goes through {@link UnifiedClient}, so the token and
 * the server URL come from the same place the rest of the app uses them.
 * The server hosts the hook library, so the quick scripts are fetched from
 * it rather than duplicated here.
 */
public class FridaFragment extends Fragment {

    private TextInputEditText serverInput;
    private TextInputEditText tokenInput;
    private TextInputEditText processInput;
    private EditText scriptInput;
    private TextView statusText;
    private TextView outputText;
    private android.widget.Spinner deviceSpinner;
    private MaterialButton connectBtn;
    private MaterialButton disconnectBtn;
    private MaterialButton runBtn;
    private MaterialButton webUiBtn;
    private WebView fridaWebView;
    private LinearLayout webViewHolder;

    private UnifiedClient client;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String savedHost = "";
    private String savedToken = "";
    private final List<String> deviceIds = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 16, 16, 16);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildConnectionCard());
        content.addView(buildConsoleCard());
        content.addView(buildQuickScriptsCard());

        // The server's own Frida page, hidden until asked for.
        webViewHolder = new LinearLayout(requireContext());
        webViewHolder.setOrientation(LinearLayout.VERTICAL);
        webViewHolder.setVisibility(View.GONE);
        fridaWebView = new WebView(requireContext());
        fridaWebView.getSettings().setJavaScriptEnabled(true);
        fridaWebView.getSettings().setDomStorageEnabled(true);
        fridaWebView.getSettings().setAllowFileAccess(true);
        fridaWebView.getSettings().setAllowContentAccess(true);
        fridaWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setStatus(getString(R.string.frida_connected, savedHost));
            }
        });
        webViewHolder.addView(fridaWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(webViewHolder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    // ------------------------------------------------------------------
    // Views
    // ------------------------------------------------------------------
    private View buildConnectionCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(12);
        card.setCardElevation(4);
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 16, 16, 16);

        TextView title = new TextView(requireContext());
        title.setText(R.string.frida_title);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inner.addView(title);

        TextInputLayout serverLayout = new TextInputLayout(requireContext());
        serverLayout.setHint(getString(R.string.frida_server_hint));
        serverInput = new TextInputEditText(requireContext());
        serverInput.setHint(getString(R.string.frida_server_url_hint));
        serverInput.setSingleLine(true);
        // Pre-fill from the shared server setting, so the server is
        // configured once and both tabs use it.
        String savedHost = SettingsDialog.unifiedHost(requireContext());
        if (savedHost != null && !savedHost.trim().isEmpty()) {
            serverInput.setText(savedHost.trim());
        }
        serverLayout.addView(serverInput);
        inner.addView(serverLayout);

        TextInputLayout tokenLayout = new TextInputLayout(requireContext());
        tokenLayout.setHint(getString(R.string.frida_token_hint));
        tokenInput = new TextInputEditText(requireContext());
        tokenInput.setHint(getString(R.string.frida_token_from_config));
        tokenInput.setSingleLine(true);
        String savedToken = SettingsDialog.unifiedToken(requireContext());
        if (savedToken != null && !savedToken.trim().isEmpty()) {
            tokenInput.setText(savedToken.trim());
        }
        tokenLayout.addView(tokenInput);
        inner.addView(tokenLayout);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        connectBtn = new MaterialButton(requireContext());
        connectBtn.setText(R.string.frida_connect);
        connectBtn.setOnClickListener(v -> connect());
        buttonRow.addView(connectBtn);

        disconnectBtn = new MaterialButton(requireContext());
        disconnectBtn.setText(R.string.frida_disconnect);
        disconnectBtn.setOnClickListener(v -> disconnect());
        disconnectBtn.setEnabled(false);
        buttonRow.addView(disconnectBtn);

        webUiBtn = new MaterialButton(requireContext());
        webUiBtn.setText(R.string.frida_web_ui);
        webUiBtn.setOnClickListener(v -> toggleWebUi());
        webUiBtn.setEnabled(false);
        buttonRow.addView(webUiBtn);

        inner.addView(buttonRow);

        statusText = new TextView(requireContext());
        statusText.setText(R.string.frida_not_connected);
        statusText.setTextSize(13f);
        inner.addView(statusText);

        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        card.setLayoutParams(lp);
        return card;
    }

    private View buildConsoleCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(12);
        card.setCardElevation(4);
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 16, 16, 16);

        // Device picker.
        LinearLayout deviceRow = new LinearLayout(requireContext());
        deviceRow.setOrientation(LinearLayout.HORIZONTAL);
        deviceRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView deviceLabel = new TextView(requireContext());
        deviceLabel.setText(R.string.frida_target_device);
        deviceRow.addView(deviceLabel);

        deviceSpinner = new android.widget.Spinner(requireContext());
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item,
                new ArrayList<String>());
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(deviceAdapter);
        deviceRow.addView(deviceSpinner);

        MaterialButton refresh = new MaterialButton(requireContext());
        refresh.setText(R.string.frida_refresh);
        refresh.setOnClickListener(v -> refreshDevices());
        deviceRow.addView(refresh);
        inner.addView(deviceRow);

        // Process target.
        TextInputLayout processLayout = new TextInputLayout(requireContext());
        processLayout.setHint(getString(R.string.frida_target_process));
        processInput = new TextInputEditText(requireContext());
        processInput.setSingleLine(true);
        processLayout.addView(processInput);
        inner.addView(processLayout);

        // Script.
        TextInputLayout scriptLayout = new TextInputLayout(requireContext());
        scriptLayout.setHint(getString(R.string.frida_script_hint));
        scriptInput = new EditText(requireContext());
        scriptInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        scriptInput.setHorizontallyScrolling(false);
        scriptInput.setMinLines(6);
        scriptInput.setTextSize(12f);
        scriptInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        scriptLayout.addView(scriptInput);
        inner.addView(scriptLayout);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton example = new MaterialButton(requireContext());
        example.setText(R.string.frida_load_example);
        example.setOnClickListener(v -> loadExample());
        buttonRow.addView(example);

        runBtn = new MaterialButton(requireContext());
        runBtn.setText(R.string.frida_run);
        runBtn.setOnClickListener(v -> runScript());
        runBtn.setEnabled(false);
        buttonRow.addView(runBtn);

        inner.addView(buttonRow);

        // Output.
        TextView outputLabel = new TextView(requireContext());
        outputLabel.setText(R.string.frida_output);
        inner.addView(outputLabel);

        outputText = new TextView(requireContext());
        outputText.setTypeface(android.graphics.Typeface.MONOSPACE);
        outputText.setTextSize(11f);
        outputText.setTextIsSelectable(true);
        outputText.setMovementMethod(new ScrollingMovementMethod());
        outputText.setMinLines(4);
        inner.addView(outputText);

        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        card.setLayoutParams(lp);
        return card;
    }

    private View buildQuickScriptsCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(12);
        card.setCardElevation(4);
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 16, 16, 16);

        TextView label = new TextView(requireContext());
        label.setText(R.string.frida_quick_scripts);
        inner.addView(label);

        MaterialButton classes = new MaterialButton(requireContext());
        classes.setText(R.string.frida_quick_enumerate_classes);
        classes.setOnClickListener(v -> scriptInput.setText(SCRIPT_ENUMERATE_CLASSES));
        inner.addView(classes);

        MaterialButton hookMethods = new MaterialButton(requireContext());
        hookMethods.setText(R.string.frida_quick_hook_methods);
        hookMethods.setOnClickListener(v -> askForClass());
        inner.addView(hookMethods);

        MaterialButton heap = new MaterialButton(requireContext());
        heap.setText(R.string.frida_quick_dump_heap);
        heap.setOnClickListener(v -> scriptInput.setText(SCRIPT_DUMP_HEAP));
        inner.addView(heap);

        MaterialButton bypassRoot = new MaterialButton(requireContext());
        bypassRoot.setText(R.string.frida_quick_bypass_root);
        bypassRoot.setOnClickListener(v -> loadHookFromServer("root_detection.js"));
        inner.addView(bypassRoot);

        card.addView(inner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        card.setLayoutParams(lp);
        return card;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------
    private void connect() {
        savedHost = serverInput.getText() != null ? serverInput.getText().toString().trim() : "";
        savedToken = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";

        if (savedHost.isEmpty()) {
            setStatus(getString(R.string.frida_error_url_required));
            return;
        }
        if (!savedHost.startsWith("http://") && !savedHost.startsWith("https://")) {
            savedHost = "http://" + savedHost;
        }
        // The Device tab talks to the same server, so remember it.
        SettingsDialog.saveUnified(requireContext(), savedHost, savedToken);
        client = new UnifiedClient(savedHost, savedToken, 15000);

        setStatus(getString(R.string.frida_connecting));
        connectBtn.setEnabled(false);

        io.execute(() -> {
            final boolean up = client.health();
            final String devicesJson = up ? client.fridaDevicesJson() : null;
            requireActivity().runOnUiThread(() -> {
                if (!up) {
                    setStatus(getString(R.string.frida_not_connected_long));
                    connectBtn.setEnabled(true);
                    return;
                }
                disconnectBtn.setEnabled(true);
                webUiBtn.setEnabled(true);
                runBtn.setEnabled(true);
                setStatus(getString(R.string.frida_status, savedHost));
                if (devicesJson != null) {
                    populateDevices(devicesJson);
                }
            });
        });
    }

    private void disconnect() {
        if (fridaWebView != null) {
            fridaWebView.loadUrl("about:blank");
        }
        webViewHolder.setVisibility(View.GONE);
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        webUiBtn.setEnabled(false);
        runBtn.setEnabled(false);
        setStatus(getString(R.string.frida_not_connected));
        savedHost = "";
        savedToken = "";
        client = null;
    }

    private void toggleWebUi() {
        if (webViewHolder.getVisibility() == View.VISIBLE) {
            webViewHolder.setVisibility(View.GONE);
            return;
        }
        if (savedHost.isEmpty()) {
            return;
        }
        webViewHolder.setVisibility(View.VISIBLE);
        fridaWebView.loadUrl(savedHost + "/frida/page?token=" + savedToken);
    }

    private void refreshDevices() {
        if (client == null) {
            toast(getString(R.string.frida_not_connected_long));
            return;
        }
        io.execute(() -> {
            final String json = client.fridaDevicesJson();
            requireActivity().runOnUiThread(() -> {
                if (json == null) {
                    toast(getString(R.string.frida_no_devices));
                    return;
                }
                populateDevices(json);
            });
        });
    }

    private void populateDevices(String json) {
        deviceIds.clear();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject) {
                    JSONObject o = (JSONObject) item;
                    String id = o.optString("id", o.optString("device", ""));
                    if (!id.isEmpty()) {
                        deviceIds.add(id);
                    }
                } else {
                    deviceIds.add(String.valueOf(item));
                }
            }
        } catch (JSONException e) {
            // A body that is not a JSON array is not a device list.
            setStatus(getString(R.string.frida_no_devices));
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, deviceIds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        if (deviceIds.isEmpty()) {
            setStatus(getString(R.string.frida_no_devices));
        }
    }

    private String selectedDevice() {
        if (deviceSpinner == null || deviceIds.isEmpty()) {
            return "local";
        }
        int pos = deviceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= deviceIds.size()) {
            return "local";
        }
        return deviceIds.get(pos);
    }

    private void runScript() {
        if (client == null) {
            toast(getString(R.string.frida_not_connected_long));
            return;
        }
        String script = scriptInput.getText() != null ? scriptInput.getText().toString() : "";
        if (script.trim().isEmpty()) {
            toast(getString(R.string.frida_script_missing));
            return;
        }
        String process = processInput.getText() != null ? processInput.getText().toString().trim() : "";
        if (process.isEmpty()) {
            toast(getString(R.string.frida_process_missing));
            return;
        }
        runBtn.setEnabled(false);
        io.execute(() -> {
            final String response = client.fridaExecuteScript(script, process);
            requireActivity().runOnUiThread(() -> {
                runBtn.setEnabled(true);
                appendOutput(response);
            });
        });
    }

    private void loadHookFromServer(String name) {
        if (client == null) {
            toast(getString(R.string.frida_not_connected_long));
            return;
        }
        io.execute(() -> {
            final String body = client.hookScript(name);
            requireActivity().runOnUiThread(() -> {
                if (body == null || body.trim().isEmpty()) {
                    toast(getString(R.string.frida_no_devices));
                    return;
                }
                scriptInput.setText(body);
                toast(getString(R.string.frida_bypass_root_loaded));
            });
        });
    }

    private void loadExample() {
        scriptInput.setText(SCRIPT_ENUMERATE_CLASSES);
        toast(getString(R.string.frida_example_loaded));
    }

    private void askForClass() {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.frida_class_prompt)
                .setView(processInput)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String cls = processInput.getText() != null
                            ? processInput.getText().toString().trim() : "";
                    if (!cls.isEmpty()) {
                        scriptInput.setText(buildHookAllMethods(cls));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void appendOutput(String response) {
        if (response == null) {
            outputText.setText("");
            return;
        }
        try {
            JSONObject o = new JSONObject(response);
            StringBuilder sb = new StringBuilder();
            if (o.has("result_id")) {
                sb.append(getString(R.string.frida_result_id, o.optString("result_id", "")))
                  .append('\n');
            }
            sb.append(o.optString("stdout", ""));
            if (!o.optString("stderr", "").isEmpty()) {
                sb.append('\n').append(o.optString("stderr", ""));
            }
            if (o.has("reason") && !o.optString("reason", "").isEmpty()) {
                sb.append('\n').append(o.optString("reason", ""));
            }
            outputText.setText(sb.toString());
        } catch (JSONException e) {
            outputText.setText(response);
        }
    }

    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void toast(String text) {
        if (isAdded()) {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        io.shutdownNow();
        if (fridaWebView != null) {
            fridaWebView.destroy();
            fridaWebView = null;
        }
        super.onDestroyView();
    }

    // ------------------------------------------------------------------
    // Example scripts
    // ------------------------------------------------------------------
    private static final String SCRIPT_ENUMERATE_CLASSES =
            "Java.perform(function () {\n"
            + "    var classes = Java.enumerateLoadedClassesSync();\n"
            + "    var interesting = classes.filter(function (c) {\n"
            + "        return c.indexOf('com.') === 0 || c.indexOf('org.') === 0\n"
            + "               || c.indexOf('net.') === 0 || c.indexOf('io.') === 0;\n"
            + "    });\n"
            + "    console.log('[classes] ' + interesting.length + ' of ' + classes.length);\n"
            + "    interesting.slice(0, 500).forEach(function (c) { console.log(c); });\n"
            + "});\n";

    private static final String SCRIPT_DUMP_HEAP =
            "Java.perform(function () {\n"
            + "    // The memory map always works, and shows what is mapped and\n"
            + "    // executable, which is what a heap dump is usually wanted for.\n"
            + "    var maps = new java.io.File('/proc/self/maps').exists()\n"
            + "        ? java.nio.file.Files.readAllBytes(\n"
            + "              new java.io.FileInputStream('/proc/self/maps')) : null;\n"
            + "    if (maps) {\n"
            + "        var text = String.fromCharCode.apply(null, maps);\n"
            + "        console.log('[maps] ' + text.split('\\n').length + ' lines');\n"
            + "        console.log(text);\n"
            + "    }\n"
            + "    // An HPROF needs the app to be debuggable, so this can fail.\n"
            + "    try {\n"
            + "        var Debug = Java.use('android.os.Debug');\n"
            + "        Debug.dumpHprofData('/data/local/tmp/bugdeck.hprof');\n"
            + "        console.log('[heap] wrote /data/local/tmp/bugdeck.hprof');\n"
            + "    } catch (e) {\n"
            + "        console.log('[heap] hprof not available: ' + e);\n"
            + "    }\n"
            + "});\n";

    private static String buildHookAllMethods(String className) {
        return "Java.perform(function () {\n"
                + "    var klass = Java.use('" + className + "');\n"
                + "    var methods = klass.class.getDeclaredMethods();\n"
                + "    var hooked = 0;\n"
                + "    methods.forEach(function (m) {\n"
                + "        var name = m.getName();\n"
                + "        var sig = [];\n"
                + "        var params = m.getParameterTypes();\n"
                + "        for (var i = 0; i < params.length; i++) sig.push(params[i].getName());\n"
                + "        var overload = klass[name].overload.apply(klass[name], sig);\n"
                + "        overload.implementation = function () {\n"
                + "            var args = Array.prototype.slice.call(arguments);\n"
                + "            console.log('[hook] " + className + ".' + name + ' ' + args.length);\n"
                + "            return overload.call(this, ...args);\n"
                + "        };\n"
                + "        hooked++;\n"
                + "    });\n"
                + "    console.log('[hook] ' + hooked + ' methods on " + className + "');\n"
                + "});\n";
    }
}
