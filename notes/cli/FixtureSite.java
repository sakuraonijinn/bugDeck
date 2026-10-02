import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Fixture site reproducing the exact false-positive cases from the three
 *  original APKs, so the CLI can be verified end to end. */
public class FixtureSite {
    public static void main(String[] a) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 8731), 0);

        // FALSE POSITIVE CASE: a perfectly normal page that merely MENTIONS the
        // scary words. SQLi Spyder flagged this "Vulnerable!" because it grepped
        // without ever fetching a clean baseline.
        page(s, "/blog/mysql-tutorial.php", 200,
            "<html><body><h1>MySQL Tutorial</h1>"
          + "<p>We call mysql_fetch_array() in every example.</p>"
          + "<p>If the query failed you'll see a syntax error.</p>"
          + "<p>Possible causes: Query failed.</p></body></html>");

        // REAL VULNERABILITY: clean normally, leaks a MySQL error on a quote.
        s.createContext("/item.php", ex -> {
            String q = ex.getRequestURI().getQuery();
            boolean injected = q != null && q.contains("'");
            String body = injected
                ? "<html><body>You have an error in your SQL syntax; "
                + "check the manual that corresponds to your MySQL server version "
                + "for the right syntax to use near ''' at line 1</body></html>"
                : "<html><body><h1>Item 42</h1><p>In stock.</p></body></html>";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });

        // A crawlable landing page. Its URLs need a query string so the SQLi
        // crawl mode has something to probe.
        page(s, "/index.html", 200,
            "<html><body>"
          + "<h1>Shop</h1>"
          + "<a href=\"/item.php?id=1\">Item 1</a>"
          + "<a href=\"/item.php?id=2\">Item 2</a>"
          + "<a href=\"/catalog.php?page=1\">Catalog</a>"
          + "<a href=\"/blog/mysql-tutorial.php\">Tutorial</a>"
          + "<a href=\"/style.css\">stylesheet</a>"
          + "<a href=\"http://elsewhere.example/offsite\">off site</a>"
          + "</body></html>");

        // a second hop, to prove depth > 1 works
        page(s, "/catalog.php", 200,
            "<html><body><a href=\"/item.php?id=99\">deep item</a></body></html>");

        // CATCH-ALL SITE: returns 200 for every unknown path. The Admin Finder
        // called EVERY wordlist entry "Found!" here.
        // NOTE: the catch-all must be registered LAST, and must be the "/"
        // context, or HttpServer's prefix matching will let an earlier context
        // shadow every later path.
        // A REAL admin panel, at a path the wordlist actually contains
        // ("login.php" is mix1 line 4). Proves true positives still survive
        // the catch-all baseline.
        page(s, "/login.php", 200,
            "<html><body><form name=\"loginform\" action=\"/login.php\" method=\"post\">"
          + "<input type=\"text\" name=\"log\" id=\"user_login\" />"
          + "<input type=\"password\" name=\"pwd\" id=\"user_pass\" />"
          + "<input type=\"submit\" value=\"Log In\" /></form></body></html>");

        // WAF interstitial served with HTTP 200
        page(s, "/protected", 200,
            "<html><head><title>Just a moment...</title></head>"
          + "<body><h1>Checking your browser before accessing</h1>"
          + "<p>Cloudflare</p></body></html>");

        // register the catch-all last: this is the soft-404 page the baseline
        // will be built from
        s.createContext("/", ex -> {
            String body = "<html><body>404 - Page not found</body></html>";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });

        s.start();
        System.out.println("fixture site on http://127.0.0.1:8731");
        Thread.sleep(600_000);
        s.stop(0);
    }

    static void page(HttpServer s, String path, int code, String body) {
        s.createContext(path, ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
    }
}
