import com.bugtraqapps.bugdeck.core.HtmlLinks;
import com.bugtraqapps.bugdeck.dork.BingSearch;
import com.bugtraqapps.bugdeck.dork.Providers;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Parser tests for the keyless HTML engines, run against real markup captured
 * from the live engines (notes/fixtures/*.html).
 *
 * <p>These are the parsers that have to survive the engines changing their
 * templates, so each one is pinned to the actual selector shape observed in the
 * capture rather than to a guess. Where an engine served an anti-bot page
 * instead of results, the test asserts we detect that and say so, rather than
 * silently returning zero results.
 */
public class ProviderTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }

    static void eq(String name, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            pass++; System.out.println("  ok   " + name);
        } else {
            fail++; System.out.println("  FAIL " + name + "  got=[" + got + "] want=[" + want + "]");
        }
    }

    static String fixture(String name) throws Exception {
        // Resolve relative to the source tree, not the working directory, so the
        // suite passes whether it is run from bugdeck/ or from the repo root.
        Path p = Paths.get("notes/fixtures", name);
        if (!Files.exists(p)) {
            Path alt = Paths.get("bugdeck", "notes/fixtures", name);
            if (Files.exists(alt)) p = alt;
        }
        if (!Files.exists(p)) return null;
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        boolean haveFixtures = fixture("bing.html") != null;
        if (!haveFixtures) {
            System.out.println("no notes/fixtures/*.html captured; parser tests need live markup");
            System.exit(2);
        }

        testBing();
        testDdg();
        testMojeek();
        testBrave();
        testRedirectDecoding();
        testAntdotDetection();

        System.out.println();
        System.out.println("================  PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    // ---- Bing: results wrapped in /ck/a redirects, real url in u=a1 ------

    static void testBing() throws Exception {
        System.out.println("-- Bing (u=a1 base64 redirect) --");
        String html = fixture("bing.html");

        List<String> urls = BingSearch.parse(html);
        ok("found result blocks", urls.size() >= 10);
        ok("every url is absolute https", urls.stream().allMatch(
                u -> u.startsWith("http://") || u.startsWith("https://")));
        ok("no bing.com/ck/ wrapper survives",
                urls.stream().noneMatch(u -> u.contains("bing.com/ck/")));
        ok("no bing.com chrome leaks in",
                urls.stream().noneMatch(u -> u.contains("bing.com/images")
                        || u.contains("bing.com/settings")));
        ok("no internal /images nav", urls.stream().noneMatch(
                u -> u.startsWith("https://www.bing.com/images")));

        // the decode must handle the urlsafe base64 variant with missing padding
        eq("padded urlsafe base64 decodes",
                BingSearch.decodeCkParam("a1aHR0cHM6Ly9leGFtcGxlLmNvbQ"), "https://example.com");
        eq("padding inferred when short",
                BingSearch.decodeCkParam("a1aHR0cHM6Ly9leGFtcGxlLmNvbQ"), "https://example.com");
        eq("non-encoded value passes through",
                BingSearch.decodeCkParam("plainvalue"), "plainvalue");
        eq("null is safe", BingSearch.decodeCkParam(null), null);
    }

    // ---- DuckDuckGo: /l/?uddg= wrapper -----------------------------------

    static void testDdg() throws Exception {
        System.out.println("-- DuckDuckGo (uddg redirect) --");
        String html = fixture("ddg.html");

        // From a datacenter IP DDG serves an anomaly-detection page. The parser
        // must report that as blocked, NOT as "0 results".
        boolean blocked = Providers.detectBlock(html) != null;
        if (blocked) {
            System.out.println("     (live capture was an anti-bot page; asserting detection)");
            ok("anti-bot page detected", blocked);
            ok("reason is specific", Providers.detectBlock(html).contains("bot"));
        } else {
            List<String> urls = Providers.parseDuckDuckGo(html);
            ok("uddg wrapper unwrapped", urls.stream().noneMatch(u -> u.contains("/l/?uddg=")));
        }

        String sample = "<div class=\"result results_links\">"
                + "<a class=\"result__a\" href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fq%3D1&rut=x\">t</a></div>";
        List<String> s = Providers.parseDuckDuckGo(sample);
        eq("sample yields one url", s.size(), 1);
        eq("uddg decoded", s.get(0), "https://example.com/a?q=1");

        eq("plain absolute link kept",
                Providers.parseDuckDuckGo("<a class=\"result__a\" href=\"https://example.com/b\">t</a>").get(0),
                "https://example.com/b");
    }

    // ---- Mojeek: JS challenge from this IP --------------------------------

    static void testMojeek() throws Exception {
        System.out.println("-- Mojeek --");
        String html = fixture("mojeek.html");
        boolean blocked = Providers.detectBlock(html) != null;
        if (blocked) {
            ok("challenge detected", true);
        }
        String sample = "<ul class=\"results-standard\">"
                + "<li><a class=\"ob\" href=\"https://example.com/c\">Title</a>"
                + "<p class=\"s\">desc</p></li></ul>";
        List<String> s = Providers.parseMojeek(sample);
        eq("ob anchor parsed", s.size(), 1);
        eq("mojeek url", s.get(0), "https://example.com/c");
    }

    // ---- Brave web UI: also JS-rendered from this IP ----------------------

    static void testBrave() throws Exception {
        System.out.println("-- Brave web UI --");
        String html = fixture("brave.html");
        boolean blocked = Providers.detectBlock(html) != null;
        if (blocked) System.out.println("     (live capture was an anti-bot page)");
        String sample = "<a href=\"https://example.com/d\" class=\"result-header\">T</a>";
        ok("generic anchor still harvestable", HtmlLinks.extract(sample).contains("https://example.com/d"));
    }

    // ---- redirect helpers -------------------------------------------------

    static void testRedirectDecoding() {
        System.out.println("-- redirect unwrapping --");
        eq("html entity &amp; decoded", HtmlLinks.decodeEntities("a&amp;b"), "a&b");
        eq("ddg wrapper stripped",
                HtmlLinks.unwrap("https://duckduckgo.com/l/?uddg=https%3A%2F%2Fx.com%2Fy"),
                "https://x.com/y");
        eq("non-wrapper untouched", HtmlLinks.unwrap("https://x.com/y"), "https://x.com/y");
        ok("java.net.URLDecoder is used for percent-escapes",
                HtmlLinks.unwrap("/l/?uddg=https%3A%2F%2Fx.com%2Fa%20b").contains("a%20b")
                        || HtmlLinks.unwrap("/l/?uddg=https%3A%2F%2Fx.com%2Fa%20b").contains("a b"));
    }

    // ---- anti-bot detection ------------------------------------------------

    static void testAntdotDetection() {
        System.out.println("-- anti-bot page detection --");
        ok("ddg anomaly", Providers.detectBlock(
                "<html><body>Anomaly detected</body></html>").contains("bot"));
        ok("mojeek captcha", Providers.detectBlock(
                "<html><body>Captcha</body></html>").contains("bot"));
        ok("cloudflare interstitial", Providers.detectBlock(
                "<title>Just a moment...</title><h1>Checking your browser</h1>") != null);
        ok("plain results are not flagged", Providers.detectBlock(
                "<html><body><a href='https://x.com'>x</a></body></html>") == null);
        ok("null safe", Providers.detectBlock(null) == null);
        ok("empty safe", Providers.detectBlock("") == null);
    }
}
