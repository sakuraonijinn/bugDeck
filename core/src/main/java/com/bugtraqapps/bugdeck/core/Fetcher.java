package com.bugtraqapps.bugdeck.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * HTTP GET with a real timeout, a real User-Agent, real request headers, and a
 * result type that distinguishes "could not reach it" from "it said no".
 *
 * <p>Every one of the original three apps had the same defect: no timeouts and an
 * empty {@code catch (IOException)} that made an unreachable host indistinguishable
 * from a clean result. {@link Fetch} makes that impossible to express.
 *
 * <p>Redirects are NOT followed by default. The original admin finder followed
 * them silently, so {@code /admin -> /login} reported "admin panel found". The
 * caller has to opt in, with an explicit hop cap so a redirect loop terminates.
 */
public final class Fetcher {

    public static final String DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
      + "Chrome/120.0.0.0 Mobile Safari/537.36";

    /** Body cap. A huge page must not be able to OOM a scan. */
    public static final int MAX_BODY_BYTES = 2_000_000;

    /** Redirect hop cap, even when following is enabled. */
    public static final int DEFAULT_MAX_REDIRECTS = 5;

    /**
     * Per-request settings. A plain {@code get(url, ua, timeoutMs)} overload is
     * kept for the scanners, which need none of this.
     */
    public static final class Opts {
        public int timeoutMs = 8000;
        public String userAgent = DEFAULT_UA;
        public final Map<String, String> headers = new LinkedHashMap<>();
        public boolean followRedirects = false;
        public int maxRedirects = DEFAULT_MAX_REDIRECTS;

        public Opts timeout(int ms) { this.timeoutMs = ms; return this; }
        public Opts ua(String ua) {
            if (ua != null && !ua.trim().isEmpty()) this.userAgent = ua;
            return this;
        }
        public Opts header(String name, String value) {
            if (name != null && value != null) this.headers.put(name, value);
            return this;
        }
        public Opts follow(int maxHops) {
            this.followRedirects = true;
            if (maxHops > 0) this.maxRedirects = maxHops;
            return this;
        }
    }

    public static final class Fetch {
        public final int status;
        public final String body;
        public final String finalUrl;
        public final boolean redirected;
        public final String failure;   // null on success
        /** Raw Location header on a 3xx, else null. Kept so callers can chain. */
        public final String location;
        /** Content-Type header, lower-cased; null when the server sent none. */
        public final String contentType;

        Fetch(int status, String body, String finalUrl, boolean redirected, String failure) {
            this(status, body, finalUrl, redirected, failure, null, null);
        }

        Fetch(int status, String body, String finalUrl, boolean redirected,
              String failure, String location) {
            this(status, body, finalUrl, redirected, failure, location, null);
        }

        Fetch(int status, String body, String finalUrl, boolean redirected,
              String failure, String location, String contentType) {
            this.status = status;
            this.body = body;
            this.finalUrl = finalUrl;
            this.redirected = redirected;
            this.failure = failure;
            this.location = location;
            this.contentType = contentType == null
                ? null : contentType.toLowerCase();
        }

        public boolean ok() { return failure == null; }

        public Verdict transportVerdict() {
            return failure == null ? Verdict.NOT_VULNERABLE : Verdict.SKIPPED;
        }
    }

    private Fetcher() {}

    /** Convenience overload used by the scanners. */
    public static Fetch get(String rawUrl, String ua, int timeoutMs) {
        return get(rawUrl, new Opts().ua(ua).timeout(timeoutMs));
    }

    public static Fetch get(String rawUrl, Opts o) {
        if (o == null) o = new Opts();
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return new Fetch(0, "", "", false, "empty url");
        }

        String start = rawUrl.trim();
        if (!start.startsWith("http://") && !start.startsWith("https://")) {
            start = "http://" + start;
        }

        String current = start;
        boolean everRedirected = false;

        for (int hop = 0; ; hop++) {
            Fetch one = single(current, o);
            if (one.failure != null) return one;

            if (!one.redirected) {
                // only re-wrap when we actually followed something, so a plain
                // success is returned untouched
                return everRedirected
                    ? new Fetch(one.status, one.body, one.finalUrl, true, null,
                                one.location, one.contentType)
                    : one;
            }

            // it IS a redirect: only follow when asked, and never past the cap
            if (!o.followRedirects) return one;
            if (hop >= o.maxRedirects) {
                return new Fetch(one.status, one.body, one.finalUrl, true,
                        "redirect limit (" + o.maxRedirects + ") reached",
                        one.location, one.contentType);
            }
            String next = resolveLocation(one.location, current);
            if (next == null) {
                return new Fetch(one.status, one.body, one.finalUrl, true,
                        "redirect with unusable Location header",
                        one.location, one.contentType);
            }
            everRedirected = true;
            current = next;
        }
    }

    /**
     * Resolve a Location header, which may be absolute, root-relative, or
     * protocol-relative. Returns null when it cannot be turned into a URL.
     */
    static String resolveLocation(String loc, String currentUrl) {
        if (loc == null || loc.trim().isEmpty()) return null;
        try {
            URL base = new URL(currentUrl);
            return new URL(base, loc.trim()).toString();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /** One request, no redirect following. Sets {@link #LOCATION} as a side effect. */
    private static Fetch single(String u, Opts o) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(u);
            URLConnection raw = url.openConnection();
            if (!(raw instanceof HttpURLConnection)) {
                return new Fetch(0, "", u, false, "not an http url");
            }
            conn = (HttpURLConnection) raw;
            conn.setRequestMethod("GET");
            // the originals had none of these, so one slow host hung the whole scan
            conn.setConnectTimeout(o.timeoutMs);
            conn.setReadTimeout(o.timeoutMs);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", o.userAgent);
            conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Connection", "close");
            for (Map.Entry<String, String> e : o.headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }

            int status = conn.getResponseCode();
            String loc = conn.getHeaderField("Location");
            String ct = conn.getHeaderField("Content-Type");

            String finalUrl = conn.getURL().toString();
            boolean redirected = status >= 300 && status < 400 && loc != null;

            InputStream in = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            String body = readAll(in, conn.getContentEncoding());
            return new Fetch(status, body, finalUrl, redirected, null, loc, ct);

        } catch (UnknownHostException e) {
            return new Fetch(0, "", u, false, "dns: " + e.getMessage());
        } catch (MalformedURLException e) {
            return new Fetch(0, "", u, false, "bad url: " + e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            return new Fetch(0, "", u, false, "timeout");
        } catch (IOException e) {
            // never swallow: the reason is carried to the UI
            return new Fetch(0, "", u, false,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in, String encoding) {
        if (in == null) return "";
        InputStream stream = in;
        try {
            if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                stream = new GZIPInputStream(in);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n, total = 0;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
                total += n;
                if (total > MAX_BODY_BYTES) break;
            }
            r.close();
            return sb.toString();
        } catch (IOException e) {
            return "";
        } finally {
            try { stream.close(); } catch (IOException ignored) { }
        }
    }
}
