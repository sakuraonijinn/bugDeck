package com.bugtraqapps.bugdeck.dork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The search backends, each with its route already built.
 *
 * <p>Two families:
 * <ul>
 *   <li><b>JSON APIs</b> — Brave and Google Programmable Search. Reliable, need a
 *       credential, and return structured results.</li>
 *   <li><b>Keyless HTML engines</b> — Bing, DuckDuckGo, Mojeek, Startpage and a
 *       SearXNG instance. No credential, but most serve an anti-bot page to
 *       automated clients from a datacenter IP. They are implemented and tested
 *       against captured markup, and report a clear BLOCKED reason rather than a
 *       misleading "0 results". See {@link Providers#detectBlock}.</li>
 * </ul>
 *
 * <p>Routes are taken from each vendor's own documented endpoint.
 */
public final class DorkProviders {

    private DorkProviders() {}

    // ---- keyless HTML engines ---------------------------------------------

    /** Bing. The one keyless engine that reliably serves markup here. */
    public static final class Bing implements DorkProvider {
        public static final String ENDPOINT = "https://www.bing.com/search";
        public static final int PER_PAGE = 10;
        public static final int MAX_PAGES = 30;    // Bing stops honouring offset past ~300

        public String id() { return "bing"; }
        public String title() { return "Bing (keyless HTML)"; }
        public boolean requiresKey() { return false; }
        public String keyHint() {
            return "No key needed. May be rate-limited or blocked from some networks.";
        }
        public int maxPages() { return MAX_PAGES; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            Map<String, String> p = new LinkedHashMap<>();
            p.put("q", q);
            p.put("first", String.valueOf(1 + (page - 1) * PER_PAGE));
            p.put("count", String.valueOf(PER_PAGE));
            return ENDPOINT + "?" + DorkQuery.encode(p);
        }

        @Override public List<String> parse(String body, String ct) {
            return BingSearch.parse(body);
        }
    }

    /** DuckDuckGo html endpoint. Frequently serves an anomaly page. */
    public static final class DuckDuckGo implements DorkProvider {
        public static final String ENDPOINT = "https://html.duckduckgo.com/html/";

        public String id() { return "ddg"; }
        public String title() { return "DuckDuckGo (keyless HTML)"; }
        public boolean requiresKey() { return false; }
        public String keyHint() {
            return "No key needed. DuckDuckGo serves an anti-bot page to most datacenter IPs.";
        }
        public int maxPages() { return 10; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            String s = String.valueOf((page - 1) * 30);
            return ENDPOINT + "?" + DorkQuery.encode(
                new LinkedHashMap<String, String>() {{
                    put("q", q);
                    put("s", String.valueOf(s));
                    put("kl", "wt-wt");
                }});
        }

        @Override public List<String> parse(String body, String ct) {
            return Providers.parseDuckDuckGo(body);
        }
    }

    /** Mojeek. Independent index, so results differ from the big engines. */
    public static final class Mojeek implements DorkProvider {
        public static final String ENDPOINT = "https://www.mojeek.com/search";
        public static final int PER_PAGE = 10;
        public static final int MAX_PAGES = 10;

        public String id() { return "mojeek"; }
        public String title() { return "Mojeek (keyless HTML)"; }
        public boolean requiresKey() { return false; }
        public String keyHint() {
            return "No key needed. Mojeek may show a captcha to datacenter IPs.";
        }
        public int maxPages() { return MAX_PAGES; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            Map<String, String> p = new LinkedHashMap<>();
            p.put("q", q);
            p.put("s", String.valueOf((page - 1) * PER_PAGE));
            return ENDPOINT + "?" + DorkQuery.encode(p);
        }

        @Override public List<String> parse(String body, String ct) {
            return Providers.parseMojeek(body);
        }
    }

    /** Startpage. Wraps Google results; POST-only in practice. */
    public static final class Startpage implements DorkProvider {
        public static final String ENDPOINT = "https://www.startpage.com/sp/search";

        public String id() { return "startpage"; }
        public String title() { return "Startpage (keyless HTML)"; }
        public boolean requiresKey() { return false; }
        public String keyHint() {
            return "No key needed. Startpage commonly answers automated requests with 303.";
        }
        public int maxPages() { return 5; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            Map<String, String> p = new LinkedHashMap<>();
            p.put("query", q);
            p.put("language", "english");
            return ENDPOINT + "?" + DorkQuery.encode(p);
        }

        @Override public List<String> parse(String body, String ct) {
            return Providers.parseStartpage(body);
        }
    }

    /**
     * A public SearXNG instance. The software is open source and a self-hosted
     * instance is the reliable option; public ones mostly rate-limit hard.
     */
    public static final class Searxng implements DorkProvider {
        public static final String DEFAULT_INSTANCE = "https://searx.be";
        public static final int PER_PAGE = 20;
        public static final int MAX_PAGES = 3;

        private final String instance;
        public Searxng() { this(System.getenv("SEARXNG_URL") != null
                ? System.getenv("SEARXNG_URL") : DEFAULT_INSTANCE); }
        public Searxng(String instance) { this.instance = instance; }

        public String id() { return "searxng"; }
        public String title() { return "SearXNG (keyless JSON)"; }
        public boolean requiresKey() { return false; }
        public String keyHint() {
            return "No key needed. Point SEARXNG_URL at your own instance to be reliable.";
        }
        public int maxPages() { return MAX_PAGES; }
        public String instance() { return instance; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            Map<String, String> p = new LinkedHashMap<>();
            p.put("q", q);
            p.put("format", "json");
            p.put("pageno", String.valueOf(page));
            return instance + "/search?" + DorkQuery.encode(p);
        }

        @Override public List<String> parse(String body, String ct) {
            List<String> urls = new ArrayList<>();
            // searxng json: {"results":[{"url":...}]}
            Map<String, Object> root = JsonLite.parseObject(body);
            Object results = root.get("results");
            if (results instanceof List) {
                for (Object o : (List<?>) results) {
                    if (o instanceof Map) {
                        Object u = ((Map<?, ?>) o).get("url");
                        if (u instanceof String) urls.add((String) u);
                    }
                }
            }
            return urls;
        }
    }

    // ---- JSON APIs (credentialed) ----------------------------------------

    /** Brave Search API. */
    public static final class Brave implements DorkProvider {
        public static final String ENDPOINT =
            "https://api.search.brave.com/res/v1/web/search";
        public static final int PER_PAGE = 20;   // max count is 20

        public String id()   { return "brave"; }
        public String title() { return "Brave Search API"; }
        public boolean requiresKey() { return true; }
        public String keyHint() {
            return "api.search.brave.com -> Subscribe -> get an API key. "
                 + "Sent as the X-Subscription-Token header, not a query parameter.";
        }
        public int maxPages() { return 10; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            // Brave paginates by offset, not page number
            int offset = Math.max(0, (page - 1) * PER_PAGE);
            Map<String, String> p = new LinkedHashMap<>();
            p.put("q", q);
            p.put("count", String.valueOf(PER_PAGE));
            p.put("offset", String.valueOf(offset));
            return ENDPOINT + "?" + DorkQuery.encode(p);
        }

        /** The token is a header, so callers need it alongside the URL. */
        public String headerName() { return "X-Subscription-Token"; }

        @Override public List<String> parse(String body, String ct) {
            return JsonLite.webResultUrls(body);
        }
    }

    /** Google Programmable Search (Custom Search JSON API). */
    public static final class GoogleCse implements DorkProvider {
        public static final String ENDPOINT =
            "https://www.googleapis.com/customsearch/v1";
        public static final int PER_PAGE = 10;

        public String id()   { return "google"; }
        public String title() { return "Google Programmable Search"; }
        public boolean requiresKey() { return true; }
        public String keyHint() {
            return "Create a Programmable Search Engine at programmablesearch.google.com, "
                 + "then enable the JSON API. Needs BOTH an API key and a search-engine "
                 + "id (cx). Google caps this API at 100 results total.";
        }
        public int maxPages() { return 10; }

        public String buildUrl(String dork, String domain, int page, String key) {
            String q = DorkQuery.build(dork, domain);
            if (q == null) return null;
            // start is 1-based and the API errors past 100 results
            int start = Math.max(1, (page - 1) * PER_PAGE + 1);
            if (start + PER_PAGE - 1 > 100) return null;
            Map<String, String> p = new LinkedHashMap<>();
            p.put("key", key == null ? "" : key);
            p.put("cx", CseIdHolder.cx);
            p.put("q", q);
            p.put("num", String.valueOf(PER_PAGE));
            p.put("start", String.valueOf(start));
            return ENDPOINT + "?" + DorkQuery.encode(p);
        }

        @Override public List<String> parse(String body, String ct) {
            return JsonLite.itemsUrls(body);
        }
    }

    /**
     * Google needs two credentials, so the cx is held alongside rather than
     * crammed into the single key field the UI exposes.
     */
    public static final class CseIdHolder {
        public static volatile String cx = "";
        private CseIdHolder() {}
    }

    // ---- registry ------------------------------------------------------

    private static final List<DorkProvider> ALL = new ArrayList<>();
    static {
        // keyless first: they need no setup
        ALL.add(new Bing());
        ALL.add(new DuckDuckGo());
        ALL.add(new Mojeek());
        ALL.add(new Startpage());
        ALL.add(new Searxng());
        // then the credentialed APIs
        ALL.add(new Brave());
        ALL.add(new GoogleCse());
    }

    public static List<DorkProvider> available() {
        return new ArrayList<>(ALL);
    }

    public static DorkProvider byId(String id) {
        for (DorkProvider p : ALL) if (p.id().equals(id)) return p;
        return null;
    }
}
