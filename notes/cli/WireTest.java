import com.bugtraqapps.bugdeck.core.*;
import com.bugtraqapps.bugdeck.dork.*;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Phase-2 coverage: the parts of the CLI that were built but never wired.
 *
 * <p>Everything here is a real HTTP round-trip against a local fixture, because
 * the gaps were in the wiring (headers, redirects, pagination), not in parsing.
 */
public class WireTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }
    static void eq(String name, Object got, Object want) {
        boolean c = String.valueOf(got).equals(String.valueOf(want));
        if (c) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name + "  got=[" + got + "] want=[" + want + "]"); }
    }

    static HttpServer server;
    static String base;
    static final Map<String, AtomicInteger> hits = new HashMap<>();

    static void route(String path, HttpHandler h) {
        final AtomicInteger n = new HashMap<String, AtomicInteger>().get(path);
        AtomicInteger counter = n == null ? new AtomicInteger() : n;
        hits.put(path, counter);
        server.createContext(path, wrap(path, h));
    }

    static HttpHandler wrap(final String path, final HttpHandler inner) {
        return new HttpHandler() {
            public void handle(HttpExchange ex) throws java.io.IOException {
                hits.get(path).incrementAndGet();
                inner.handle(ex);
            }
        };
    }

    static void send(HttpExchange ex, int code, String body, String ct) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", ct);
        ex.sendResponseHeaders(code, b.length);
        OutputStream os = ex.getResponseBody();
        os.write(b);
        os.close();
    }

    public static void main(String[] a) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;

        // ---- routes ----
        route("/plain", ex -> send(ex, 200, "<html>hello</html>", "text/html"));
        route("/r1", ex -> { ex.getResponseHeaders().add("Location", "/r2"); send(ex, 302, "", "text/html"); });
        route("/r2", ex -> { ex.getResponseHeaders().add("Location", "/r3"); send(ex, 302, "", "text/html"); });
        route("/r3", ex -> send(ex, 200, "<html>arrived</html>", "text/html"));
        route("/loop", ex -> { ex.getResponseHeaders().add("Location", "/loop"); send(ex, 302, "", "text/html"); });
        route("/needsauth", ex -> {
            String tok = ex.getRequestHeaders().getFirst("X-Subscription-Token");
            if (tok == null || !tok.equals("SECRET")) { send(ex, 401, "no token", "application/json"); return; }
            send(ex, 200, "{\"web\":{\"results\":[{\"url\":\"https://secret.example/a\"}]}}", "application/json");
        });
        route("/dorkpage1", ex -> send(ex, 200,
                "{\"web\":{\"results\":["
                + "{\"url\":\"https://x.example/1\"},{\"url\":\"https://x.example/2\"}]}}",
                "application/json"));
        route("/dorkpage2", ex -> send(ex, 200,
                "{\"web\":{\"results\":[{\"url\":\"https://x.example/3\"}]}}", "application/json"));
        route("/dorkempty", ex -> send(ex, 200, "{\"web\":{\"results\":[]}}", "application/json"));
        route("/dorkerr", ex -> send(ex, 500, "boom", "text/plain"));

        // crawler graph, all explicit contexts (prefix shadowing bit us before)
        route("/index.html", ex -> send(ex, 200,
                "<html><a href=\"/a.php?id=1\">a</a>"
                + "<a href=\"/b.php?id=2\">b</a>"
                + "<a href=\"/nested/\">n</a>"
                + "<a href=\"http://other.example/x\">ext</a>"
                + "<a href=\"/style.css\">css</a>"
                + "<a href=\"javascript:void(0)\">js</a></html>", "text/html"));
        route("/a.php", ex -> send(ex, 200, "<html><a href=\"/deep.php?id=9\">d</a></html>", "text/html"));
        route("/b.php", ex -> send(ex, 200, "<html>nothing</html>", "text/html"));
        route("/deep.php", ex -> send(ex, 200, "<html>deep</html>", "text/html"));
        route("/nested", ex -> send(ex, 200, "<html><a href=\"/index.html\">back</a></html>", "text/html"));
        route("/style.css", ex -> send(ex, 200, "body{}", "text/css"));

        // HttpServer defaults to a single-threaded executor, which would serialise
        // every request and make the concurrency measurement meaningless.
        java.util.concurrent.ExecutorService serverPool =
            java.util.concurrent.Executors.newFixedThreadPool(24);
        server.setExecutor(serverPool);
        server.start();

        try {
            testFetcher();
            testDork();
            testCrawler();
        } finally {
            server.stop(0);
            // stop(0) does not touch the executor we handed it, and those
            // non-daemon threads keep the JVM alive forever. Without this the
            // whole suite passes and then hangs the build.
            serverPool.shutdownNow();
        }

        System.out.println();
        System.out.println("================  PASS=" + pass + "  FAIL=" + fail);
        // Same reason: exit explicitly so a lingering thread cannot wedge the build.
        System.exit(fail > 0 ? 1 : 0);
    }

    // ---- Fetcher: headers, redirects, caps ------------------------------

    static void testFetcher() {
        System.out.println("-- Fetcher: auth header reaches the server --");
        Fetcher.Opts o = new Fetcher.Opts();
        o.headers.put("X-Subscription-Token", "SECRET");
        Fetcher.Fetch f = Fetcher.get(base + "/needsauth", o);
        eq("status 200 when header sent", f.status, 200);
        ok("token never put in the URL", !f.finalUrl.contains("SECRET"));
        ok("body parsed", f.body.contains("secret.example"));

        Fetcher.Fetch g = Fetcher.get(base + "/needsauth", new Fetcher.Opts());
        eq("401 without the header", g.status, 401);

        System.out.println();
        System.out.println("-- Fetcher: redirect handling --");
        Fetcher.Opts noFollow = new Fetcher.Opts();
        Fetcher.Fetch r1 = Fetcher.get(base + "/r1", noFollow);
        eq("not followed by default", r1.status, 302);
        ok("flagged as redirected", r1.redirected);

        Fetcher.Opts follow = new Fetcher.Opts();
        follow.followRedirects = true;
        Fetcher.Fetch r2 = Fetcher.get(base + "/r1", follow);
        eq("followed to the end", r2.status, 200);
        ok("final url reported", r2.finalUrl.endsWith("/r3"));
        ok("body is the final page", r2.body.contains("arrived"));

        Fetcher.Opts capped = new Fetcher.Opts();
        capped.followRedirects = true;
        capped.maxRedirects = 1;
        Fetcher.Fetch r3 = Fetcher.get(base + "/r1", capped);
        ok("hop cap enforced, not infinite", r3.status == 302 || r3.status == 0);

        Fetcher.Opts loopOpts = new Fetcher.Opts();
        loopOpts.followRedirects = true;
        long t0 = System.currentTimeMillis();
        Fetcher.Fetch loop = Fetcher.get(base + "/loop", loopOpts);
        ok("redirect loop terminates", System.currentTimeMillis() - t0 < 15000);

        System.out.println();
        System.out.println("-- Fetcher: input validation --");
        ok("empty url -> SKIPPED-ish failure", !Fetcher.get("", new Fetcher.Opts()).ok());
        ok("garbage url reports a reason",
                Fetcher.get("http://", new Fetcher.Opts()).failure != null);
        Fetcher.Opts noSchema = new Fetcher.Opts();
        ok("bare host still resolves", Fetcher.get("127.0.0.1:" + base.split(":")[2] + "/plain", noSchema).status == 200);
    }

    // ---- Dork: the dead wiring ------------------------------------------

    static void testDork() {
        System.out.println("-- Dork: a real request with the right credential --");
        DorkSearch s = new DorkSearch();
        DorkProvider brave = DorkProviders.byId("brave");
        ok("brave provider exists", brave != null);

        // Point the request at the fixture; the provider still supplies the
        // header and the response parsing, which is what we are testing.
        DorkSearch.Result r = s.search(brave, "inurl:admin", "example.com", 1, "SECRET",
                base + "/needsauth");
        eq("200 with correct header", r.httpStatus, 200);
        eq("one url parsed", r.urls.size(), 1);
        eq("url correct", r.urls.get(0), "https://secret.example/a");

        System.out.println();
        System.out.println("-- Dork: missing credential is an error, not a crash --");
        // Refusing before the request is the correct behaviour: it cannot burn
        // a request and it cannot be mistaken for "0 results".
        DorkSearch.Result noKey = s.search(brave, "inurl:admin", "example.com", 1, "", base + "/needsauth");
        eq("refused before requesting", noKey.httpStatus, 0);
        ok("names the missing credential", noKey.note.contains("no API key"));
        ok("no urls from a config error", noKey.urls.isEmpty());

        DorkSearch.Result gNoCx = s.search(DorkProviders.byId("google"), "x", "e.com", 1, "KEY", base + "/x");
        ok("google without cx is rejected", gNoCx.httpStatus == 0 && gNoCx.urls.isEmpty());

        System.out.println();
        System.out.println("-- Dork: pagination stops on an empty page --");
        AtomicInteger p1 = new AtomicInteger(), p2 = new AtomicInteger();
        DorkProvider scripted = new StubProvider("stub", p1, p2);
        DorkSearch.Result pg = s.searchAllPages(scripted, "x", "e.com", "", 5, null);
        ok("stopped instead of looping forever", p1.get() + p2.get() <= 6);
        eq("collected all three across pages", pg.urls.size(), 3);

        System.out.println();
        System.out.println("-- Dork: transport failure is reported, never silent --");
        DorkSearch.Result dead = s.search(brave, "x", "e.com", 1, "SECRET", "http://127.0.0.1:1/none");
        eq("dead port -> status 0", dead.httpStatus, 0);
        ok("failure note present", dead.note != null && dead.note.contains("refus")
                || (dead.note != null && !dead.note.isEmpty()));
        eq("no urls", dead.urls.size(), 0);
    }

    /** Serves two fixture pages then goes empty, to prove pagination terminates. */
    static final class StubProvider implements DorkProvider {
        final String id; final AtomicInteger p1, p2;
        StubProvider(String id, AtomicInteger p1, AtomicInteger p2) { this.id = id; this.p1 = p1; this.p2 = p2; }
        public String id() { return id; }
        public String title() { return "stub"; }
        public boolean requiresKey() { return false; }
        public String keyHint() { return ""; }
        public int maxPages() { return 5; }
        public String buildUrl(String dork, String domain, int page, String key) {
            return base + (page == 1 ? "/dorkpage1" : page == 2 ? "/dorkpage2" : "/dorkempty");
        }
        public List<String> parse(String body, String ct) {
            return DorkProviders.byId("brave").parse(body, "application/json");
        }
    }

    // ---- Crawler: the dropped SQLi mode ---------------------------------

    static void testCrawler() throws InterruptedException {
        System.out.println("-- Crawler: same-host, depth and asset rules --");
        Crawler c = new Crawler(base + "/index.html", Fetcher.DEFAULT_UA, 5000);
        Crawler.Result res = c.crawl(2, 20, true);
        Set<String> urls = new HashSet<>(res.urls);

        ok("found the seeded query urls", urls.contains(base + "/a.php?id=1"));
        ok("found the deep url past depth 1", urls.contains(base + "/deep.php?id=9"));
        ok("stayed on the seed host",
                urls.stream().noneMatch(u -> u.contains("other.example")));
        ok("skipped the stylesheet", urls.stream().noneMatch(u -> u.endsWith(".css")));
        ok("skipped javascript: urls", urls.stream().noneMatch(u -> u.startsWith("javascript:")));
        ok("deduped (no repeats)", res.urls.size() == urls.size());
        ok("did not loop forever", res.urls.size() <= 20);
        ok("fetched the entry page", hits.get("/index.html").get() == 1);

        System.out.println();
        System.out.println("-- Crawler: page cap is honoured --");
        Crawler capped = new Crawler(base + "/index.html", Fetcher.DEFAULT_UA, 5000);
        Crawler.Result lim = capped.crawl(5, 3, true);
        ok("returned at most the cap", lim.urls.size() <= 3);

        System.out.println();
        System.out.println("-- Crawler: unreachable seed is reported, not silent --");
        Crawler dead = new Crawler("http://127.0.0.1:1/", Fetcher.DEFAULT_UA, 1500);
        Crawler.Result d = dead.crawl(2, 10, true);
        eq("no urls", d.urls.size(), 0);
        ok("reason recorded for the user",
                d.failure != null || !d.failures.isEmpty());
        ok("reason is not empty",
                (d.failure != null && !d.failure.isEmpty())
                        || (d.failures.size() > 0 && d.failures.get(0).length() > 5));

        System.out.println();
        System.out.println("-- Crawler: a malformed seed is rejected up front --");
        Crawler junk = new Crawler("http://", Fetcher.DEFAULT_UA, 1000);
        ok("bad seed -> failure, no urls",
                junk.crawl(2, 10, true).urls.isEmpty()
                        && junk.crawl(2, 10, true).failure != null);

        System.out.println();
        System.out.println("-- AdminFinder: perHostCap is actually enforced --");
        // a deliberately slow endpoint, so overlapping requests are observable
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        route("/slow", ex -> {
            int now = concurrent.incrementAndGet();
            synchronized (peak) { if (now > peak.get()) peak.set(now); }
            try { Thread.sleep(150); } catch (InterruptedException ignored) { }
            concurrent.decrementAndGet();
            send(ex, 200, "<html>ok</html>", "text/html");
        });

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 24; i++) many.add("/slow?i=" + i);

        // cap of 3 while running 12 threads: concurrency must never exceed 3
        AdminFinder gated = new AdminFinder(Fetcher.DEFAULT_UA, 5000, 12, 3);
        gated.scan(base, many);
        ok("peak concurrency <= cap (" + peak.get() + " <= 3)", peak.get() <= 3);
        ok("but real work still happened", peak.get() > 1);

        // uncapped: must exceed the cap, proving the gate is what limited it
        peak.set(0);
        AdminFinder ungated = new AdminFinder(Fetcher.DEFAULT_UA, 5000, 12, 0);
        ungated.scan(base, many);
        ok("without a cap it goes higher (" + peak.get() + " > 3)", peak.get() > 3);
    }
}
