import com.bugtraqapps.bugdeck.core.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Spins up a local server that reproduces the exact false-positive cases
 *  the original three apps got wrong, then asserts the new core gets them right. */
public class CoreTest {

    static int PASS = 0, FAIL = 0;

    static void check(String name, Object got, Object want) {
        if (Objects.equals(got, want)) { PASS++; System.out.println("  ok   " + name); }
        else { FAIL++; System.out.println("  FAIL " + name + "  got=" + got + " want=" + want); }
    }

    static void send(HttpServer s, String path, int code, String body) {
        s.createContext(path, ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
    }

    public static void main(String[] a) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // 1. a NORMAL page that happens to mention the scary words.
        //    The originals flagged this "Vulnerable!". We must not.
        send(s, "/normal.php", 200,
            "<html><body><h1>MySQL Tutorial</h1><p>We use mysql_fetch_array() in our examples. " +
            "Possible query failed.</p><p>Syntax error discussion below.</p></body></html>");

        // 2. a genuinely injectable page: clean, then leaks a real MySQL error on quote
        send(s, "/vuln.php", 200, "<html><body><h1>Search</h1><p>No results.</p></body></html>");

        // 3. catch-all soft 404: returns 200 for everything
        send(s, "/wild", 200, "<html><body>Page not found</body></html>");

        // 4. a real login form
        send(s, "/realadmin", 200,
            "<html><body><form action=\"/login\" method=post>" +
            "<input type=\"text\" name=\"username\"><input type=\"password\" name=\"password\">" +
            "<button>Log In</button></form></body></html>");

        // 5. WAF block page
        send(s, "/waf", 200, "<html><body>Checking your browser before accessing</body></html>");

        // 6. redirect
        s.createContext("/redir", ex -> {
            ex.getResponseHeaders().add("Location", "/realadmin");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });

        // 7. explicit handler: HttpServer matches contexts by PREFIX, so
        // /realadmin2 would otherwise be served the /realadmin login page.
        send(s, "/realadmin2", 200, "<html><body>Page not found</body></html>");

        s.start();
        String base = "http://127.0.0.1:" + s.getAddress().getPort();

        System.out.println("\n-- Signature unit --");
        check("soft404 detects 200 not-found", Signature.isSoft404(200, "Page not found"), true);
        check("login form is not soft404", Signature.isSoft404(200,
            "<input type=password name=password>Page not found"), false);
        check("hasLoginForm", Signature.hasLoginForm("<input type=\"password\">"), true);
        check("mysql error matched", Signature.matchDbError(
            "You have an error in your SQL syntax; check near 'x'' at line 1") != null, true);
        check("generic 'Syntax error' alone NOT matched",
            Signature.matchDbError("some syntax error occurred") != null, false);
        check("waf matched", Signature.matchWaf("Checking your browser before accessing") != null, true);
        check("normalize strips tags", Signature.normalize("<b>hi</b> there"), "hi there");

        System.out.println("\n-- SQLi differential (the big one) --");
        SqlInjector inj = new SqlInjector("test-agent", 5000);
        SqlInjector.Result r1 = inj.probe(base + "/normal.php");
        check("normal page with scary words -> NOT_VULNERABLE", r1.verdict, Verdict.NOT_VULNERABLE);

        // simulate injection: probe (url + ') returns a real MySQL error.
        // NOTE: the guard must test for the injected quote, not just "q=",
        // otherwise the baseline errors too and nothing is provable.
        HttpServer s2 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s2.createContext("/x.php", ex -> {
            String q = ex.getRequestURI().getQuery();
            String body = (q != null && q.contains("q='"))
                ? "You have an error in your SQL syntax; check the manual near ''' at line 1"
                : "<html><body>Search results</body></html>";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        s2.start();
        String b2 = "http://127.0.0.1:" + s2.getAddress().getPort() + "/x.php?q=";
        SqlInjector.Result r2 = inj.probe(b2);
        check("real injection -> VULNERABLE", r2.verdict, Verdict.VULNERABLE);
        check("  evidence non-empty", r2.evidence != null && !r2.evidence.isEmpty(), true);

        System.out.println("\n-- unreachable host is SKIPPED not 'clean' --");
        SqlInjector.Result r3 = inj.probe("http://no-such-host-xyz-12345.invalid/");
        check("dns failure -> SKIPPED", r3.verdict, Verdict.SKIPPED);
        check("  failure reason kept", r3.evidence.startsWith("baseline"), true);

        System.out.println("\n-- Admin finder --");
        AdminFinder af = new AdminFinder("test-agent", 5000, 4, 5);
        check("soft-404 path -> NOT_VULNERABLE",
            af.check(base + "/wild").verdict, Verdict.NOT_VULNERABLE);
        check("real login form -> VULNERABLE",
            af.check(base + "/realadmin").verdict, Verdict.VULNERABLE);
        check("login form noted", af.check(base + "/realadmin").note, "login form");
        check("redirect -> BLOCKED", af.check(base + "/redir").verdict, Verdict.BLOCKED);
        check("403 -> BLOCKED", Signature.judgeAdminPath(403, ""), Verdict.BLOCKED);
        check("404 -> NOT_VULNERABLE", Signature.judgeAdminPath(404, ""), Verdict.NOT_VULNERABLE);
        check("waf body -> BLOCKED", af.check(base + "/waf").verdict, Verdict.BLOCKED);

        System.out.println("\n-- %EXT% expansion (never implemented in original) --");
        List<String> ex = AdminFinder.expand("administrator/login.%EXT%");
        check("expands to 10 variants", ex.size(), 10);
        check("  contains login.php", ex.contains("administrator/login.php"), true);
        check("  contains login.aspx", ex.contains("administrator/login.aspx"), true);
        check("non-token entry passes through",
            AdminFinder.expand("admin/").size(), 1);

        System.out.println("\n-- url join --");
        check("join trims slash", AdminFinder.join("http://x.com/", "admin/"), "http://x.com/admin/");
        check("join adds slash", AdminFinder.join("http://x.com", "admin/"), "http://x.com/admin/");

        System.out.println("\n-- sticky-status regression: an unreachable host must not inherit a hit --");
        // "/realadmin" duplicated on purpose: dedup is correct behaviour, so the
        // expectation is 3 results, not 4.
        List<String> wl = Arrays.asList("/realadmin", "/realadmin",
                 "http://no-such-host-xyz-12345.invalid/p", "/realadmin2");
        List<AdminFinder.Hit> hits = af.scan(base, wl, null);
        check("dedup applied -> 3 results", hits.size(), 3);
        long vuln = hits.stream().filter(h -> h.verdict == Verdict.VULNERABLE).count();
        // /realadmin is a genuine login form; /realadmin2 falls through to the
        // catch-all soft-404. Exactly one real hit, never two.
        check("exactly 1 genuine hit (no sticky leak)", vuln, 1L);
        boolean anySkipped = hits.stream().anyMatch(h -> h.verdict == Verdict.SKIPPED);
        check("unreachable host reported as SKIPPED", anySkipped, true);
        boolean skippedHasReason = hits.stream()
            .filter(h -> h.verdict == Verdict.SKIPPED)
            .allMatch(h -> h.note != null && !h.note.isEmpty());
        check("skipped carries a reason", skippedHasReason, true);
        check("join leaves absolute url alone",
            AdminFinder.join("http://x.com", "http://y.com/a"), "http://y.com/a");

        s.stop(0); s2.stop(0);
        System.out.println("\n================  PASS=" + PASS + "  FAIL=" + FAIL + "  ================");
        System.exit(FAIL == 0 ? 0 : 1);
    }
}
