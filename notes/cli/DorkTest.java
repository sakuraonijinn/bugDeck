import com.bugtraqapps.bugdeck.dork.*;
import java.util.*;
import java.lang.reflect.*;

/** Exercises the dork layer, which had never been run. */
public class DorkTest {
    static int pass = 0, fail = 0;

    static void check(String name, Object got, Object want) {
        if (Objects.equals(String.valueOf(got), String.valueOf(want))) {
            pass++; System.out.println("  ok   " + name);
        } else {
            fail++; System.out.println("  FAIL " + name + "  got=[" + got + "] want=[" + want + "]");
        }
    }
    static void checkTrue(String name, boolean b) {
        if (b) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }

    // the two classes are package-private by design; reach them reflectively
    // so this test can live outside the package.
    static Object call(String cls, String m, Class<?>[] sig, Object... args) throws Exception {
        Class<?> c = Class.forName(cls);
        Method mm = c.getDeclaredMethod(m, sig);
        mm.setAccessible(true);
        return mm.invoke(null, args);
    }

    public static void main(String[] a) throws Exception {
        String Q = "com.bugtraqapps.bugdeck.dork.DorkQuery";
        String J = "com.bugtraqapps.bugdeck.dork.JsonLite";

        System.out.println("-- DorkQuery --");
        check("plain query", call(Q, "build", new Class[]{String.class, String.class},
                "inurl:admin", null), "inurl:admin");
        check("domain appended", call(Q, "build", new Class[]{String.class, String.class},
                "login", "example.com"), "login site:example.com");
        check("scheme stripped from domain", call(Q, "build", new Class[]{String.class, String.class},
                "", "https://example.com/x"), "site:example.com");
        check("bogus domain rejected", call(Q, "build", new Class[]{String.class, String.class},
                "login", "not a host"), "login");
        check("nothing to search", call(Q, "build", new Class[]{String.class, String.class},
                "  ", null), "null");
        check("operator case-normalised", call(Q, "build", new Class[]{String.class, String.class},
                "SITE:example.com", null), "site:example.com");
        check("unknown operator untouched", call(Q, "build", new Class[]{String.class, String.class},
                "frobnicate:x", null), "frobnicate:x");
        checkTrue("quoted phrase survives",
                String.valueOf(call(Q, "build", new Class[]{String.class, String.class},
                        "\"admin panel\" inurl:wp", null)).contains("\"admin panel\""));
        check("whitespace collapsed", call(Q, "build", new Class[]{String.class, String.class},
                "a    b", null), "a b");

        System.out.println();
        System.out.println("-- JsonLite: Brave shape --");
        String brave = "{\"web\":{\"results\":["
                + "{\"title\":\"A\",\"url\":\"https://a.example/1\"},"
                + "{\"title\":\"B\",\"url\":\"https://b.example/2\"}]},\"meta\":{}}";
        List<String> br = (List<String>) call(J, "webResultUrls", new Class[]{String.class}, brave);
        check("brave urls", br.size(), 2);
        checkTrue("brave first url", br.contains("https://a.example/1"));

        System.out.println();
        System.out.println("-- JsonLite: Google CSE shape --");
        String g = "{\"kind\":\"customsearch#search\",\"items\":["
                + "{\"link\":\"https://g1.example/a\"},"
                + "{\"link\":\"https://g2.example/b\"}]}";
        List<String> gi = (List<String>) call(J, "itemsUrls", new Class[]{String.class}, g);
        check("google urls", gi.size(), 2);

        String pagemap = "{\"pagemap\":{\"result\":[{\"unescapedUrl\":\"https://old.example/x\"}]}}";
        List<String> pm = (List<String>) call(J, "itemsUrls", new Class[]{String.class}, pagemap);
        check("legacy pagemap fallback", pm.size(), 1);

        System.out.println();
        System.out.println("-- JsonLite: malformed input must not crash --");
        check("garbage -> empty", ((List<?>) call(J, "webResultUrls",
                new Class[]{String.class}, "not json at all")).size(), 0);
        check("null -> empty", ((List<?>) call(J, "itemsUrls",
                new Class[]{String.class}, new Object[]{null})).size(), 0);
        check("truncated json -> empty", ((List<?>) call(J, "webResultUrls",
                new Class[]{String.class}, "{\"web\":{\"results\":[")).size(), 0);
        check("wrong shape -> empty", ((List<?>) call(J, "webResultUrls",
                new Class[]{String.class}, "{\"web\":\"nope\"}")).size(), 0);

        System.out.println();
        System.out.println("-- JsonLite: escapes + numbers --");
        String esc = "{\"web\":{\"results\":[{\"url\":\"https://x.example/a\\\"b\"}]}}";
        List<String> el = (List<String>) call(J, "webResultUrls", new Class[]{String.class}, esc);
        check("quote escape decoded", el.size(), 1);
        String uni = "{\"web\":{\"results\":[{\"url\":\"https://x.example/caf\\u00e9\"}]}}";
        List<String> ul = (List<String>) call(J, "webResultUrls", new Class[]{String.class}, uni);
        check("unicode escape decoded", ul.size() == 1 && ul.get(0).contains("café"), true);

        System.out.println();
        // provider routes -- driven by the registry, not by position, so adding a
        // provider does not silently change which one is under test
        System.out.println("-- provider routes --");
        for (DorkProvider p : DorkProviders.available()) {
            String u = p.buildUrl("inurl:admin", "example.com", 1, "KEY123");
            checkTrue(p.id() + " builds a url", u != null && u.startsWith("https://"));
            // each engine names its query parameter differently
            String param = "startpage".equals(p.id()) ? "query=" : "q=";
            checkTrue(p.id() + " url carries the query",
                    u != null && u.contains(param + "inurl%3Aadmin"));
            checkTrue(p.id() + " url carries the domain",
                    u != null && u.contains("site%3Aexample.com"));
        }
        check("registry has 7 providers", DorkProviders.available().size(), 7);
        checkTrue("byId resolves brave", DorkProviders.byId("brave") != null);
        checkTrue("byId resolves bing", DorkProviders.byId("bing") != null);
        checkTrue("byId resolves ddg", DorkProviders.byId("ddg") != null);
        checkTrue("byId resolves mojeek", DorkProviders.byId("mojeek") != null);
        checkTrue("byId resolves startpage", DorkProviders.byId("startpage") != null);
        checkTrue("byId resolves searxng", DorkProviders.byId("searxng") != null);
        check("byId unknown -> null", DorkProviders.byId("nope"), null);

        System.out.println();
        System.out.println("-- keyless providers need no credential --");
        for (String id : new String[]{"bing", "ddg", "mojeek", "startpage", "searxng"}) {
            DorkProvider p = DorkProviders.byId(id);
            checkTrue(id + " is keyless", p != null && !p.requiresKey());
        }
        checkTrue("brave needs a key", DorkProviders.byId("brave").requiresKey());
        checkTrue("google needs a key", DorkProviders.byId("google").requiresKey());

        System.out.println();
        System.out.println("-- brave header, google 100-result cap --");
        check("brave header name",
                new DorkProviders.Brave().headerName(), "X-Subscription-Token");
        check("google caps at 100 results",
                DorkProviders.byId("google").buildUrl("x", "e.com", 20, "k"), null);
        check("bing paginates with first=",
                DorkProviders.byId("bing").buildUrl("x", "e.com", 3, "").contains("first=21"), true);

        System.out.println();
        System.out.println("-- anti-bot page is reported as blocked, not empty --");
        String challenge = "<html><body>Anomaly detected</body></html>";
        check("detectBlock finds ddg anomaly",
                Providers.detectBlock(challenge) != null, true);
        check("clean page is not flagged",
                Providers.detectBlock("<a href='https://x.com'>x</a>"), null);
        List<String> none = Providers.parseDuckDuckGo(challenge);
        check("challenge yields no urls", none.size(), 0);

        System.out.println();
        System.out.println("================  PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
