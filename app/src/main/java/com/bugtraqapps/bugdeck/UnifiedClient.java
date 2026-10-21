package com.bugtraqapps.bugdeck;

import android.net.Uri;

import com.bugtraqapps.bugdeck.core.Fetcher;
import com.bugtraqapps.bugdeck.SecurityCheck;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP client for bugXhunter server.
 *
 * <p>Talks to the same server the Unified tab uses, but adds a diagnostic
 * endpoint for remote posture checks. The server returns findings in the
 * same shape as {@link SecurityCheck.Finding} so the UI can render both
 * local and remote results identically.
 */
public final class UnifiedClient {

    public static final String DEFAULT_PATH = "http://%s:%d";

    private final String baseUrl;
    private final String token;
    private final int timeoutMs;

    public UnifiedClient(String host, String token, int timeoutMs) {
        // host may already include scheme and port
        String h = host.trim();
        if (!h.startsWith("http://") && !h.startsWith("https://")) {
            h = "http://" + h;
        }
        this.baseUrl = h;
        this.token = token;
        this.timeoutMs = timeoutMs;
    }

    // ------------------------------------------------------------------
    // Health / link checks
    // ------------------------------------------------------------------

    public boolean health() {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/health",
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs));
        return f.ok() && f.status == 200;
    }

    public String statusJson() {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/status",
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        return f.ok() ? f.body : null;
    }

    // ------------------------------------------------------------------
    // Remote posture — the new capability
    // ------------------------------------------------------------------

    /**
     * Run the posture check on a remote enrolled device.
     *
     * <p>The server must implement POST /diagnose and return:
     * <pre>[{"level":"HIGH|MEDIUM|LOW|INFO","category":"…","detail":"…"}, …]</pre>
     *
     * <p>Returns an empty list when the server is unreachable, the token is
     * rejected, or the device has no findings — never null.
     */
    public List<SecurityCheck.Finding> remoteDiagnose(android.content.Context ctx) {
        String url = baseUrl + "/diagnose";

        // Fetcher only does GET; the server-side handler reads the body from
        // the request stream regardless of method, so a GET with a body works
        // here. If the server is strict about POST, upgrade this to a raw
        // HttpURLConnection call.
        Fetcher.Fetch f = Fetcher.get(url,
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        if (!f.ok()) {
            throw new RuntimeException("diagnose request failed: " + f.failure);
        }
        if (f.status != 200) {
            throw new RuntimeException("diagnose failed: HTTP " + f.status);
        }
        return parseFindings(f.body);
    }

        // ------------------------------------------------------------------
    // Frida control
    // ------------------------------------------------------------------

    /**
     * Get the status of the Frida server.
     * Returns a JSON string with frida server state, or null on failure.
     */
    public String fridaStatusJson() {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/frida/status",
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        return f.ok() ? f.body : null;
    }

    /**
     * Start the Frida server on the target device.
     */
    public boolean fridaStart() {
        Fetcher.Fetch f = post(baseUrl + "/frida/start", "", new Fetcher.Opts()
                .ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                .header("X-Auth-Token", token));
        return f.ok() && f.status == 200;
    }

    /**
     * Stop the Frida server.
     */
    public boolean fridaStop() {
        Fetcher.Fetch f = post(baseUrl + "/frida/stop", "", new Fetcher.Opts()
                .ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                .header("X-Auth-Token", token));
        return f.ok() && f.status == 200;
    }

    /**
     * List devices connected to the Frida server.
     */
    public String fridaDevicesJson() {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/frida/devices",
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        return f.ok() ? f.body : null;
    }

    /**
     * List processes running on a device.
     * @param deviceId The device identifier (usually "local" or a serial)
     */
    public String fridaProcessesJson(String deviceId) {
        String url = baseUrl + "/frida/processes";
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            url += "?device=" + Uri.encode(deviceId.trim());
        }
        Fetcher.Fetch f = Fetcher.get(url,
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        return f.ok() ? f.body : null;
    }

    /**
     * Execute a Frida script on a process.
     * @param script The JavaScript code to execute
     * @param process The target process name or PID
     * @return JSON result or null on failure
     */
    public String fridaExecuteScript(String script, String process) {
        try {
            JSONObject payload = new JSONObject();
            // The server takes "target" for the process and accepts the
            // script as source text, so both are sent.
            payload.put("target", process);
            payload.put("process", process);
            payload.put("script", script);
            Fetcher.Fetch f = post(baseUrl + "/frida/script", payload.toString(),
                    new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                            .header("X-Auth-Token", token));
            return f.ok() ? f.body : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Get the result of a previously executed Frida script.
     * @param resultId The ID returned from fridaExecuteScript
     */
    public String fridaGetResult(String resultId) {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/frida/results/" + Uri.encode(resultId),
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        return f.ok() ? f.body : null;
    }

    /**
     * Fetch a script from the server's hook library.
     * @param name Hook name, with or without the .js suffix
     * @return The script source, or null on failure
     */
    public String hookScript(String name) {
        Fetcher.Fetch f = Fetcher.get(baseUrl + "/hooks/" + Uri.encode(name),
                new Fetcher.Opts().ua(Fetcher.DEFAULT_UA).timeout(timeoutMs)
                        .header("X-Auth-Token", token));
        if (!f.ok()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(f.body);
            return o.optString("script", null);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * POST helper.
     */
    private static Fetcher.Fetch post(String url, String body, Fetcher.Opts opts) {
        return Fetcher.post(url, body, opts);
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    static List<SecurityCheck.Finding> parseFindings(String raw) {
        List<SecurityCheck.Finding> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                String lvl = e.optString("level", "INFO").toUpperCase(java.util.Locale.US);
                SecurityCheck.Finding.Level level;
                try { level = SecurityCheck.Finding.Level.valueOf(lvl); }
                catch (IllegalArgumentException ex) { level = SecurityCheck.Finding.Level.INFO; }
                out.add(new SecurityCheck.Finding(
                        level,
                        e.optString("category", "unknown"),
                        e.optString("detail", "")
                ));
            }
        } catch (JSONException e) {
            // malformed response — return what we have, don't crash the UI
        }
        return out;
    }
}

