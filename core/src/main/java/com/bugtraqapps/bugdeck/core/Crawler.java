package com.bugtraqapps.bugdeck.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Same-host link crawler.
 *
 * <p>This is the SQLi Spyder's "Site crawl" mode, which was dropped when the
 * CLI was first written: the {@code sqli} command only probed URLs you handed
 * it, so there was no way to scan a whole site.
 *
 * <p>Deliberately conservative, because a crawler that wanders is how you end
 * up hammering someone else's infrastructure:
 * <ul>
 *   <li>stays on the seed host unless {@code allowCrossHost} is set,</li>
 *   <li>never follows off-site redirects,</li>
 *   <li>skips assets and non-HTML schemes,</li>
 *   <li>enforces a depth limit and a total page cap,</li>
 *   <li>de-duplicates by normalised URL, so a link farm of loops terminates.</li>
 * </ul>
 */
public final class Crawler {

    private final String seedUrl;

    public static final class Result {
        /** Discovered URLs in visit order. */
        public final List<String> urls = new ArrayList<>();
        /** Pages actually fetched. */
        public int pagesFetched;
        /** Pages that failed, with the reason. */
        public final List<String> failures = new ArrayList<>();
        /** Null unless the seed itself could not be read. */
        public String failure;
    }

    private static final String[] ASSET_EXT = {
        ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico",
        ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".pdf", ".zip", ".rar",
        ".gz", ".tgz", ".dmg", ".exe", ".apk", ".doc", ".docx", ".xls", ".xlsx"
    };

    private static final String[] NON_HTTP = {
        "javascript:", "mailto:", "tel:", "sms:", "data:", "ftp:", "file:", "#"
    };

    private final String seedHost;
    private final String userAgent;
    private final int timeoutMs;

    public Crawler(String seedUrl, String userAgent, int timeoutMs) {
        this.seedUrl = seedUrl;
        this.seedHost = hostOf(seedUrl);
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Crawl from the seed URL.
     *
     * @param maxDepth      link hops from the seed; 0 = seed page only
     * @param maxPages      hard cap on total pages fetched
     * @param sameHostOnly  do not follow links to other hosts
     */
    public Result crawl(int maxDepth, int maxPages, boolean sameHostOnly) {
        Result res = new Result();
        String seed = normalize(seedUrl);
        if (seed == null) {
            res.failure = "not a usable url: " + seedUrl;
            return res;
        }

        Set<String> seen = new LinkedHashSet<>();
        seen.add(stripFragment(seed));
        Deque<Entry> queue = new ArrayDeque<>();
        queue.add(new Entry(seed, 0));

        while (!queue.isEmpty() && res.pagesFetched < maxPages) {
            Entry e = queue.poll();
            Fetcher.Opts o = new Fetcher.Opts().ua(userAgent).timeout(timeoutMs);
            // no redirect following: a 302 to a login page is not a page to parse
            Fetcher.Fetch f = Fetcher.get(e.url, o);

            if (!f.ok()) {
                res.failures.add(e.url + "  (" + f.failure + ")");
                continue;
            }
            if (f.redirected) {
                // record it, but do not wander off
                res.failures.add(e.url + "  (redirect, not followed)");
                continue;
            }
            if (f.status < 200 || f.status >= 300) continue;

            String ct = f.contentType;
            if (ct != null && !ct.toLowerCase().contains("html")) continue;

            res.pagesFetched++;
            res.urls.add(e.url);

            if (e.depth >= maxDepth) continue;

            for (String href : HtmlLinks.extract(f.body, true)) {
                String abs = absolutize(e.url, href);
                if (abs == null) continue;
                if (isAsset(abs)) continue;
                String key = stripFragment(abs);
                if (!seen.add(key)) continue;
                if (sameHostOnly && !hostOf(key).equalsIgnoreCase(seedHost)) continue;
                queue.add(new Entry(key, e.depth + 1));
            }
        }
        return res;
    }

    // ---- url helpers ----------------------------------------------------

    /** Host of a URL, or empty when it cannot be parsed. */
    static String hostOf(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        try {
            String h = new java.net.URL(u).getHost();
            return h == null ? "" : h.toLowerCase();
        } catch (java.net.MalformedURLException e) {
            return "";
        }
    }

    static String normalize(String rawUrl) {
        if (rawUrl == null) return null;
        String u = rawUrl.trim();
        if (u.isEmpty()) return null;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        try {
            java.net.URL parsed = new java.net.URL(u);
            // "http://" parses happily but has no host; that is not a crawl seed
            if (parsed.getHost() == null || parsed.getHost().isEmpty()) return null;
            return u;
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    /** Resolve an href against the page it was found on. Null if unusable. */
    static String absolutize(String pageUrl, String href) {
        if (href == null) return null;
        String h = href.trim();
        if (h.isEmpty()) return null;
        String lower = h.toLowerCase();
        for (String s : NON_HTTP) if (lower.startsWith(s)) return null;

        if (lower.startsWith("//")) {
            h = (pageUrl.startsWith("https://") ? "https:" : "http:") + h;
        }
        try {
            return new java.net.URL(new java.net.URL(pageUrl), h).toString();
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    static boolean isAsset(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        for (String ext : ASSET_EXT) if (u.endsWith(ext)) return true;
        return false;
    }

    static String stripFragment(String url) {
        int h = url.indexOf('#');
        return h >= 0 ? url.substring(0, h) : url;
    }

    private static final class Entry {
        final String url; final int depth;
        Entry(String url, int depth) { this.url = url; this.depth = depth; }
    }
}
