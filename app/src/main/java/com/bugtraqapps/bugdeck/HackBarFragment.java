package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bugtraqapps.bugdeck.core.Fetcher;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HackBar tab: append a payload to a URL parameter and view the response.
 *
 * <p>The injected parameter is real: the first query parameter in the target is
 * selected, and the payload is substituted in its place. The previous
 * behaviour appended a synthetic {@code x=} parameter, so the request was not
 * testing the parameter the user had actually pasted in.
 */
public final class HackBarFragment extends Fragment {

    private TextInputEditText targetInput;
    private Spinner categorySpinner;
    private Spinner payloadSpinner;
    private MaterialButton sendBtn;
    private MaterialButton copyBtn;
    private TextInputEditText generatedUrl;
    private TextView responseView;

    private final List<String> categories = new ArrayList<>();
    private final List<String> categoryKeys = new ArrayList<>();
    private List<String> currentPayloads = new ArrayList<>();

    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        loadPayloads();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_hackbar, container, false);

        targetInput = v.findViewById(R.id.hackbar_target);
        categorySpinner = v.findViewById(R.id.hackbar_category);
        payloadSpinner = v.findViewById(R.id.hackbar_payload);
        sendBtn = v.findViewById(R.id.hackbar_send);
        copyBtn = v.findViewById(R.id.hackbar_copy);
        generatedUrl = v.findViewById(R.id.hackbar_generated);
        responseView = v.findViewById(R.id.hackbar_response);

        ArrayAdapter<String> cat = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, categories);
        cat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(cat);

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                updatePayloads(categoryKeys.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // regenerate the URL whenever the target or the chosen payload changes
        targetInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateGenerated(); }
        });
        payloadSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                updateGenerated();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        sendBtn.setOnClickListener(x -> send());
        copyBtn.setOnClickListener(x -> copyUrl());

        return v;
    }

    // ---- payloads -------------------------------------------------------

    private void loadPayloads() {
        try (InputStream is = requireContext().getAssets().open("payloads.json")) {
            JSONObject root = new JSONObject(readAll(is));
            // org.json keys() is an Iterator, not a Collection, so it cannot seed
            // an ArrayList directly.
            List<String> keys = new ArrayList<>();
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext()) keys.add(it.next());
            java.util.Collections.sort(keys);
            for (String key : keys) {
                JSONObject entry = root.getJSONObject(key);
                List<String> list = new ArrayList<>();
                JSONArray arr = entry.optJSONArray("payloads");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) list.add(arr.optString(i));
                }
                categoryKeys.add(key);
                categories.add(entry.optString("title", key));
                Payloads.putTitle(key, entry.optString("title", key));
                Payloads.put(key, list);
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.payloads_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void updatePayloads(String key) {
        currentPayloads = Payloads.get(key);
        ArrayAdapter<String> a = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, currentPayloads);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payloadSpinner.setAdapter(a);
        updateGenerated();
    }

    /**
     * Substitute the selected payload into the first query parameter.
     * If there is no query at all, the payload is added as {@code q=...}.
     */
    private void updateGenerated() {
        String target = text(targetInput);
        Object sel = payloadSpinner.getSelectedItem();
        if (target.isEmpty() || sel == null) {
            generatedUrl.setText("");
            return;
        }
        String payload = String.valueOf(sel);
        int q = target.indexOf('?');
        if (q < 0) {
            generatedUrl.setText(target + "?q=" + payload);
            return;
        }
        String before = target.substring(0, q + 1);
        String after = target.substring(q + 1);
        if (after.isEmpty()) {
            generatedUrl.setText(before + "q=" + payload);
            return;
        }
        // first key=value pair, without disturbing the rest of the query
        int amp = after.indexOf('&');
        String firstPair = amp < 0 ? after : after.substring(0, amp);
        String rest = amp < 0 ? "" : after.substring(amp);
        int eq = firstPair.indexOf('=');
        String name = eq < 0 ? "q" : firstPair.substring(0, eq);
        generatedUrl.setText(before + name + "=" + payload + rest);
    }

    // ---- request --------------------------------------------------------

    private void send() {
        final String url = text(generatedUrl);
        if (url.isEmpty()) {
            Toast.makeText(requireContext(), R.string.need_generated_url, Toast.LENGTH_SHORT).show();
            return;
        }
        final int timeout = SettingsDialog.timeoutMs(requireContext());
        final String ua = SettingsDialog.userAgent(requireContext());

        sendBtn.setEnabled(false);
        responseView.setText(R.string.sending);

        executor.execute(() -> {
            final Fetcher.Fetch f = Fetcher.get(url, ua, timeout);
            mainHandler.post(() -> {
                sendBtn.setEnabled(true);
                if (!f.ok()) {
                    responseView.setText(getString(R.string.fetch_failed, f.failure));
                    return;
                }
                String head = getString(R.string.status_fmt, f.status);
                if (f.redirected) {
                    head += "\n" + getString(R.string.redirected_fmt, f.finalUrl);
                }
                responseView.setText(head + "\n\n" + f.body);
            });
        });
    }

    private static String text(TextInputEditText f) {
        CharSequence c = f.getText();
        return c == null ? "" : c.toString().trim();
    }

    private void copyUrl() {
        String url = text(generatedUrl);
        if (url.isEmpty()) return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("BugDeck", url));
            Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
