package com.bugtraqapps.bugdeck.dork;

import com.bugtraqapps.bugdeck.core.Fetcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Actually performs a dork search against a provider.
 *
 * <p>This class existed only as dead code before: {@link DorkProviders} could
 * build a URL and parse a response, but nothing ever issued the request, so the
 * Brave subscription token was never sent and no provider was reachable. This
 * is the missing half.
 *
 * <p>Credential handling is the important part. Brave expects its key in the
 * {@code X-Subscription-Token} header, not in the query string, because a URL
 * is logged by proxies and shows up in shell history; Google CSE has no choice
 * but a query parameter. Both paths are handled so the key never leaks into a
 * URL that gets printed.
 */
public final class DorkSearch {

    public static final class Result {
        /** HTTP status, or 0 when the request never completed. */
        public int httpStatus;
        /** Result URLs, de-duplicated, in order. */
        public final List<String> urls = new ArrayList<>();
        /** Failure or caveat for the user; null when everything was fine. */
        public String note;
        /** The full request URL, minus any credential. Safe to print. */
        public String requestUrl;
        /** True when the engine served an anti-bot page rather than results. */
        public boolean blocked;

        public boolean failed() { return httpStatus == 0 || httpStatus >= 400 || blocked; }
    }

    private final String userAgent;
    private final int timeoutMs;

    public DorkSearch() { this(Fetcher.DEFAULT_UA, 10000); }

    public DorkSearch(String userAgent, int timeoutMs) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Run one search.
     *
     * @param provider  backend to use
     * @param dork      operator expression, may be empty if domain is given
     * @param domain    optional site: restriction, may be empty
     * @param page      1-based result page
     * @param key       credential; may be empty only when {@code requiresKey()} is false
     * @param urlOverride replace the request URL wholesale, while still using the
     *                     provider's headers and response parsing. Test seam only;
     *                     pass null in normal use.
     */
    public Result search(DorkProvider provider, String dork, String domain,
                         int page, String key, String urlOverride) {
        Result r = new Result();
        if (provider == null) {
            r.note = "no provider";
            return r;
        }

        // Refuse before spending a request: a missing credential is a config
        // error, and reporting it as "0 results" would be a lie.
        if (provider.requiresKey()) {
            if (key == null || key.trim().isEmpty()) {
                r.note = "no API key configured for " + provider.title();
                return r;
            }
            if (provider instanceof DorkProviders.GoogleCse
                    && (DorkProviders.CseIdHolder.cx == null
                        || DorkProviders.CseIdHolder.cx.trim().isEmpty())) {
                r.note = "Google Programmable Search also needs a search-engine id (cx)";
                return r;
            }
        }

        String url = provider.buildUrl(dork, domain, page, key);
        if (url == null) {
            r.note = "cannot build a query for those inputs";
            return r;
        }
        if (urlOverride != null && !urlOverride.trim().isEmpty()) url = urlOverride.trim();
        r.requestUrl = redact(url, key);

        Fetcher.Opts o = new Fetcher.Opts().ua(userAgent).timeout(timeoutMs);
        if (provider instanceof DorkProviders.Brave) {
            // header, not query param: URLs get logged and cached
            o.header(((DorkProviders.Brave) provider).headerName(), key);
        }

        Fetcher.Fetch f = Fetcher.get(url, o);
        r.httpStatus = f.status;

        if (!f.ok()) {
            r.note = f.failure;
            return r;
        }
        if (f.status >= 400) {
            r.note = describeHttpError(f.status, f.body);
            return r;
        }

        // An anti-bot page arrives with HTTP 200 and no results. Reporting "0
        // results" there would be a lie: the query was never actually run.
        String blocked = Providers.detectBlock(f.body);
        if (blocked != null) {
            r.blocked = true;
            r.note = provider.title() + " " + blocked;
            return r;
        }

        r.urls.addAll(provider.parse(f.body, f.contentType));
        if (r.note == null && r.urls.isEmpty()) {
            r.note = "no results on this page";
        }
        return r;
    }

    /**
     * Fetch one page and walk backwards until an empty page or the provider's
     * cap, de-duplicating as it goes.
     *
     * @param maxPages hard ceiling regardless of what the provider claims
     */
    public Result searchAllPages(DorkProvider provider, String dork, String domain,
                                 String key, int maxPages, String urlOverride) {
        Result all = new Result();
        Set<String> seen = new LinkedHashSet<>();
        int cap = Math.min(maxPages, provider == null ? 1 : Math.max(1, provider.maxPages()));

        for (int page = 1; page <= cap; page++) {
            Result one = search(provider, dork, domain, page, key, urlOverride);
            if (page == 1) {
                all.httpStatus = one.httpStatus;
                all.requestUrl = one.requestUrl;
                all.blocked = one.blocked;
            }
            if (one.blocked) {
                all.note = one.note;
                break;      // a challenge page will not differ on the next page
            }
            if (one.note != null && !one.note.equals("no results on this page")) {
                all.note = one.note;
            }
            int before = seen.size();
            seen.addAll(one.urls);

            // an empty page means we are past the end: stop rather than
            // hammering the API for pages that cannot exist
            if (one.urls.isEmpty()) {
                if (one.note == null) all.note = null;
                break;
            }
            if (seen.size() == before) break;   // this page was all duplicates
        }
        all.urls.addAll(seen);
        if (all.urls.isEmpty() && all.note == null) all.note = "no results";
        return all;
    }

    // ---- helpers --------------------------------------------------------

    /** Strip the credential out of a URL so it is safe to print or log. */
    static String redact(String url, String key) {
        if (key == null || key.trim().isEmpty() || url == null) return url;
        return url.replace(key, "***");
    }

    /**
     * Turn an error status into something a user can act on. Search APIs return
     * a JSON error object, so the message is usually in the body.
     */
    static String describeHttpError(int status, String body) {
        String msg = "";
        if (body != null) {
            int i = body.toLowerCase().indexOf("\"message\"");
            if (i >= 0) {
                int q1 = body.indexOf('"', body.indexOf(':', i) + 1);
                int q2 = q1 >= 0 ? body.indexOf('"', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) {
                    msg = ": " + body.substring(q1 + 1, q2);
                }
            }
        }
        switch (status) {
            case 400: return "bad request - check the query syntax" + msg;
            case 401: return "API key rejected or missing" + msg;
            case 403: return "key lacks access to this endpoint (or is not enabled)" + msg;
            case 429: return "rate limited - slow down or upgrade the plan" + msg;
            case 500: return "provider error" + msg;
            default:  return "http " + status + msg;
        }
    }
}
