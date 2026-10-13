package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

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
 * Frida control tab.
 *
 * <p>Connects to a bugXhunter server (configured in Settings) and provides:
 * <ul>
 *   <li>Frida server start/stop</li>
 *   <li>Device/process enumeration</li>
 *   <li>Script execution with live output</li>
 * </ul>
 */
public class FridaFragment extends Fragment {

    private TextInputEditText serverInput;
    private TextInputEditText tokenInput;
    private Spinner deviceSpinner;
    private Spinner processSpinner;
    private TextInputEditText scriptInput;
    private TextView statusText;
    private TextView outputText;
    private Button btnStartFrida;
    private Button btnStopFrida;
    private Button btnRefreshDevices;
    private Button btnLoadScript;
    private Button btnRunScript;
    private LinearLayout resultContainer;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private UnifiedClient client;
    private final List<DeviceInfo> devices = new ArrayList<>();
    private final List<ProcessInfo> processes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        // Server connection inputs
        TextInputLayout serverLayout = new TextInputLayout(getContext(), null, com.google.android.material.R.attr.textInputStyle);
        serverLayout.setHint("bugXhunter server URL");
        serverInput = new TextInputEditText(getContext());
        serverInput.setHint("e.g., http://10.0.0.1:7080");
        serverLayout.addView(serverInput);
        root.addView(serverLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextInputLayout tokenLayout = new TextInputLayout(getContext(), null, com.google.android.material.R.attr.textInputStyle);
        tokenLayout.setHint("Auth token");
        tokenInput = new TextInputEditText(getContext());
        tokenInput.setHint("from bugXhunter config.json");
        tokenLayout.addView(tokenInput);
        root.addView(tokenLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton connectBtn = new MaterialButton(getContext(), null, com.google.android.material.R.style.Widget_Material3_Button);
        connectBtn.setText("Connect");
        connectBtn.setOnClickListener(v -> connect());
        root.addView(connectBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider1 = new View(getContext());
        divider1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider1);

        // Frida control
        statusText = new TextView(getContext());
        statusText.setText("Frida server: not connected");
        statusText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        root.addView(statusText);

        LinearLayout buttonRow = new LinearLayout(getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        btnStartFrida = new Button(getContext());
        btnStartFrida.setText("Start Frida");
        btnStartFrida.setOnClickListener(v -> startFrida());
        buttonRow.addView(btnStartFrida);

        btnStopFrida = new Button(getContext());
        btnStopFrida.setText("Stop Frida");
        btnStopFrida.setOnClickListener(v -> stopFrida());
        buttonRow.addView(btnStopFrida);

        btnRefreshDevices = new Button(getContext());
        btnRefreshDevices.setText("Refresh Devices");
        btnRefreshDevices.setOnClickListener(v -> refreshDevices());
        buttonRow.addView(btnRefreshDevices);
        root.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Device selector
        TextView devLabel = new TextView(getContext());
        devLabel.setText("Target device:");
        root.addView(devLabel);
        deviceSpinner = new Spinner(getContext());
        root.addView(deviceSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Process selector
        TextView procLabel = new TextView(getContext());
        procLabel.setText("Target process:");
        root.addView(procLabel);
        processSpinner = new Spinner(getContext());
        root.addView(processSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Script editor
        TextInputLayout scriptLayout = new TextInputLayout(getContext(), null, com.google.android.material.R.attr.textInputStyle);
        scriptLayout.setHint("Frida script");
        scriptInput = new TextInputEditText(getContext());
        scriptInput.setHint("JavaScript code");
        scriptInput.setMinHeight(300);
        scriptLayout.addView(scriptInput);
        root.addView(scriptLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Load example script button
        btnLoadScript = new Button(getContext());
        btnLoadScript.setText("Load example script");
        btnLoadScript.setOnClickListener(v -> loadExampleScript());
        root.addView(btnLoadScript);

        // Run button
        btnRunScript = new Button(getContext());
        btnRunScript.setText("Run Script");
        btnRunScript.setOnClickListener(v -> runScript());
        btnRunScript.setEnabled(false);
        root.addView(btnRunScript);

        View divider2 = new View(getContext());
        divider2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider2);

        // Output
        TextView outputLabel = new TextView(getContext());
        outputLabel.setText("Output:");
        root.addView(outputLabel);

        ScrollView scrollView = new ScrollView(getContext());
        outputText = new TextView(getContext());
        outputText.setTextIsSelectable(true);
        outputText.setPadding(8, 8, 8, 8);
        scrollView.addView(outputText);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        // Quick example scripts
        addQuickScripts(root);

        return root;
    }

    private void addQuickScripts(LinearLayout parent) {
        TextView label = new TextView(getContext());
        label.setText("Quick scripts:");
        parent.addView(label);

        String[] scripts = {
                "Enumerate loaded classes",
                "Hook all methods in class",
                "Dump heap",
                "Bypass root check"
        };

        for (String name : scripts) {
            Button btn = new Button(getContext());
            btn.setText(name);
            btn.setOnClickListener(v -> {
                switch (name) {
                    case "Enumerate loaded classes":
                        scriptInput.setText(
                                "Java.enumerateLoadedClasses({" +
                                "  onMatch: function(cls) {" +
                                "    console.log(cls);" +
                                "  }," +
                                "  onComplete: function() {}" +
                                "});"
                        );
                        break;
                    case "Hook all methods in class":
                        scriptInput.setText(
                                "Java.perform(function() {" +
                                "  var cls = Java.use('java.lang.String');" +
                                "  cls.getClass().getDeclaredMethods().forEach(function(m) {" +
                                "    cls[m.getName()].overload(m.getParameterTypes()).implementation = function() {" +
                                "      console.log('Called: ' + m.getName());" +
                                "      return this[m.getName()].apply(this, arguments);" +
                                "    };" +
                                "  });" +
                                "});"
                        );
                        break;
                    case "Dump heap":
                        scriptInput.setText(
                                "Java.perform(function() {" +
                                "  console.log('Heap snapshot...');" +
                                "  Process.enumerateRanges('r--').forEach(function(r) {" +
                                "    console.log(r.base + ' ' + r.size);" +
                                "  });" +
                                "});"
                        );
                        break;
                    case "Bypass root check":
                        scriptInput.setText(
                                "Java.perform(function() {" +
                                "  var cls = Java.use('java.lang.ProcessManager');" +
                                "  cls.exec.implementation = function(cmd) {" +
                                "    console.log('Bypassed root check for: ' + cmd);" +
                                "    return null;" +
                                "  };" +
                                "});"
                        );
                        break;
                }
            });
            parent.addView(btn);
        }
    }

    private void connect() {
        String host = serverInput.getText() != null ? serverInput.getText().toString().trim() : "";
        String token = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";

        if (host.isEmpty()) {
            statusText.setText("Error: server URL required");
            return;
        }

        client = new UnifiedClient(host, token, 10000);
        executor.execute(() -> {
            boolean connected = client.health();
            main.post(() -> {
                statusText.setText(connected ? "Connected to " + host : "Connection failed");
                btnRunScript.setEnabled(connected);
            });
        });
    }

    private void startFrida() {
        if (client == null) {
            statusText.setText("Not connected");
            return;
        }
        executor.execute(() -> {
            boolean ok = client.fridaStart();
            main.post(() -> statusText.setText(ok ? "Frida started" : "Failed to start Frida"));
            if (ok) refreshDevices();
        });
    }

    private void stopFrida() {
        if (client == null) return;
        executor.execute(() -> {
            boolean ok = client.fridaStop();
            main.post(() -> statusText.setText(ok ? "Frida stopped" : "Failed to stop Frida"));
        });
    }

    private void refreshDevices() {
        if (client == null) return;
        executor.execute(() -> {
            String result = client.fridaDevicesJson();
            main.post(() -> {
                devices.clear();
                if (result != null) {
                    try {
                        JSONArray arr = new JSONArray(result);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            devices.add(new DeviceInfo(
                                    obj.optString("id", ""),
                                    obj.optString("name", "unknown"),
                                    obj.optString("type", "")
                            ));
                        }
                    } catch (JSONException e) {
                        statusText.setText("Parse error: " + e.getMessage());
                    }
                }
                ArrayAdapter<DeviceInfo> adapter = new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, devices);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                deviceSpinner.setAdapter(adapter);
                statusText.setText("Devices: " + devices.size());
            });
        });
    }

    private void loadExampleScript() {
        scriptInput.setText(
                "Java.perform(function() {" +
                "\n  console.log('Frida script loaded');" +
                "\n  Java.enumerateLoadedClasses({" +
                "\n    onMatch: function(cls) {" +
                "\n      console.log(cls);" +
                "\n    }," +
                "\n    onComplete: function() {}" +
                "\n  });" +
                "\n});"
        );
    }

    private void runScript() {
        if (client == null) {
            appendOutput("Not connected");
            return;
        }

        String script = scriptInput.getText() != null ? scriptInput.getText().toString() : "";
        if (script.isEmpty()) {
            appendOutput("Script is empty");
            return;
        }

        String process = "current";
        if (processes.size() > 0) {
            process = processes.get(processSpinner.getSelectedItemPosition()).name;
        }

        appendOutput("Executing script on: " + process);
        executor.execute(() -> {
            String result = client.fridaExecuteScript(script, process);
            main.post(() -> {
                if (result != null) {
                    appendOutput("Result:\n" + result);
                } else {
                    appendOutput("Script execution failed");
                }
            });
        });
    }

    private void appendOutput(String text) {
        String current = outputText.getText() != null ? outputText.getText().toString() : "";
        outputText.setText(current + "\n" + text);
    }

    // Data models
    private static class DeviceInfo {
        final String id, name, type;
        DeviceInfo(String id, String name, String type) {
            this.id = id; this.name = name; this.type = type;
        }
        @NonNull public String toString() { return name + " (" + id + ")"; }
    }

    private static class ProcessInfo {
        final String pid, name;
        ProcessInfo(String pid, String name) { this.pid = pid; this.name = name; }
        @NonNull public String toString() { return name; }
    }
}
