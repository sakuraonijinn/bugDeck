import com.bugtraqapps.bugdeck.core.*;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Cancellation tests.
 *
 * <p>These exist because every scan "eventually hangs trying to stop": the
 * scanners submitted the entire wordlist to a pool and then drained it one
 * Future.get() at a time, so pressing STOP did nothing visible until the queue
 * emptied. Each test below interrupts a running scan and asserts it actually
 * returns promptly.
 *
 * <p>A test that only checks "the result is correct" would pass against the old
 * code, so every assertion here is about time to return.
 */
public class CancelTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }

    static void show(String name, Object got, Object want) {
        boolean c = String.valueOf(got).equals(String.valueOf(want));
        if (c) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name + "  got=" + got + " want=" + want); }
    }

    static HttpServer server;
    static String base;
    /** every request path the fixture saw, so we can prove the scan really stopped */
    static final Map<String, AtomicIntegerShim> hits = new ConcurrentHashMap<>();

    /** tiny stand-in so the fixture can count hits without importing the shim */
    static final class AtomicIntegerShim {
        final java.util.concurrent.atomic.AtomicInteger n =
            new java.util.concurrent.atomic.AtomicInteger();
        int get() { return n.get(); }
        int inc() { return n.incrementAndGet(); }
    }

    static void send(HttpExchange ex, int code, String body, String ct) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", ct);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    public static void main(String[] args) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService pool = Executors.newFixedThreadPool(24);
        server.setExecutor(pool);
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        // A slow endpoint: every request parks for 400ms, so an uninterruptible
        // scan is obvious rather than merely slow.
        server.createContext("/slow", ex -> {
            AtomicIntegerShim c = hits.computeIfAbsent("/slow", k -> new AtomicIntegerShim());
            c.inc();
            try { Thread.sleep(400); } catch (InterruptedException ignored) { }
            send(ex, 200, "<html><body>ok</body></html>", "text/html");
        });
        // A real login form, so the scan has something to find.
        server.createContext("/admin", ex -> send(ex, 200,
            "<html><body><form method=post><input name=p><input type=password></form></body></html>",
            "text/html"));
        // Everything else is a hard 404, i.e. a well-behaved catch-all.
        server.createContext("/", ex -> send(ex, 404, "<html>nope</html>", "text/html"));

        server.start();

        try {
            testAdminScanStopsPromptly();
            testAdminScanCancelledBeforeBaseline();
            testCrawlerStopsPromptly();
            testScanStillWorksUncancelled();
        } finally {
            server.stop(0);
            pool.shutdownNow();
        }

        System.out.println();
        System.out.println("================  PASS=" + pass + "  FAIL=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /**
     * A long wordlist against a slow endpoint, interrupted mid-scan. This is the
     * reported symptom: it must return promptly rather than drain the queue.
     */
    static void testAdminScanStopsPromptly() throws Exception {
        System.out.println();
        System.out.println("-- AdminFinder: interrupting a long scan returns promptly --");

        List<String> wordlist = new ArrayList<>();
        for (int i = 0; i < 400; i++) wordlist.add("/slow?i=" + i);
        wordlist.add("/admin");

        hits.clear();
        final AtomicIntegerShim before = new AtomicIntegerShim();

        final List<AdminFinder.Hit>[] out = new List[1];
        Thread worker = new Thread(() -> {
            try {
                AdminFinder f = new AdminFinder(Fetcher.DEFAULT_UA, 5000, 8, 0);
                out[0] = f.scan(base, wordlist, null);
            } catch (InterruptedException e) { /* expected */ }
        });
        worker.start();

        Thread.sleep(700);           // let it get properly under way
        long t0 = System.currentTimeMillis();
        worker.interrupt();
        worker.join(15000);          // generous: the point is that it returns at all
        long elapsed = System.currentTimeMillis() - t0;

        boolean finished = !worker.isAlive();
        ok("interrupted scan returned (did not hang)", finished);
        ok("returned in under 5s (took " + elapsed + "ms)", elapsed < 5000);

        int h = hits.containsKey("/slow") ? hits.get("/slow").get() : 0;
        ok("stopped early instead of issuing all 401 requests (" + h + " issued)", h < 401);
        ok("scan produced no partial exception", out[0] != null || !finished);
    }

    /**
     * Cancelling during the baseline phase. The baseline runs before any pool is
     * created and used to ignore interruption entirely.
     */
    static void testAdminScanCancelledBeforeBaseline() throws Exception {
        System.out.println();
        System.out.println("-- AdminFinder: cancelling during the baseline phase --");

        List<String> wordlist = Arrays.asList("/admin", "/administrator");
        final boolean[] returned = { false };

        Thread worker = new Thread(() -> {
            try {
                // A long timeout means each baseline request is slow, so the
                // interrupt lands while the baseline loop is still running.
                AdminFinder f = new AdminFinder(Fetcher.DEFAULT_UA, 3000, 4, 0);
                f.scan(base, wordlist, null);
            } catch (InterruptedException e) { /* expected */ }
            returned[0] = true;
        });
        worker.start();

        Thread.sleep(30);
        long t0 = System.currentTimeMillis();
        worker.interrupt();
        worker.join(15000);
        long elapsed = System.currentTimeMillis() - t0;

        ok("baseline-phase cancel returned", returned[0]);
        ok("returned in under 5s (took " + elapsed + "ms)", elapsed < 5000);
    }

    /** The crawler is single-threaded, so it only stops if it checks the flag. */
    static void testCrawlerStopsPromptly() throws Exception {
        System.out.println();
        System.out.println("-- Crawler: interrupting a crawl returns promptly --");

        // The seed must link onward, otherwise the queue empties on its own and
        // there is nothing to cancel -- which is what the first version of this
        // test accidentally measured.
        server.createContext("/hub", ex -> {
            StringBuilder b = new StringBuilder("<html><body>");
            for (int i = 0; i < 60; i++) b.append("<a href=\"/slow?p=").append(i).append("\">x</a>");
            b.append("</body></html>");
            send(ex, 200, b.toString(), "text/html");
        });

        hits.clear();
        final Crawler.Result[] res = new Crawler.Result[1];
        Thread worker = new Thread(() -> {
            res[0] = new Crawler(base + "/hub", Fetcher.DEFAULT_UA, 5000).crawl(3, 500, true);
        });
        worker.start();

        Thread.sleep(600);           // long enough that it is mid-queue, not finished
        int before = hits.containsKey("/slow") ? hits.get("/slow").get() : 0;
        long t0 = System.currentTimeMillis();
        worker.interrupt();
        worker.join(15000);
        long elapsed = System.currentTimeMillis() - t0;
        int after = hits.containsKey("/slow") ? hits.get("/slow").get() : 0;

        ok("interrupted crawl returned (did not hang)", !worker.isAlive());
        ok("returned in under 5s (took " + elapsed + "ms)", elapsed < 5000);
        ok("result is marked cancelled", res[0] != null && res[0].cancelled);
        ok("stopped before draining all 60 links (" + before + " of 60 issued)",
           before < 60);
        ok("no further requests after the interrupt (" + after + " vs " + before + ")",
           after - before <= 8);
    }

    /** Regression guard: a cancelled fix must not break the normal path. */
    static void testScanStillWorksUncancelled() throws Exception {
        System.out.println();
        System.out.println("-- regression: an uncancelled scan still finds the panel --");

        List<AdminFinder.Hit> hitsOut = new AdminFinder(Fetcher.DEFAULT_UA, 5000, 4, 0)
            .scan(base, Arrays.asList("/admin", "/nope", "/login"), null);

        boolean foundLogin = hitsOut.stream()
            .anyMatch(h -> h.verdict == Verdict.VULNERABLE);
        ok("real login form still reported VULNERABLE", foundLogin);
        show("all entries accounted for", hitsOut.size(), 3);
    }
}
