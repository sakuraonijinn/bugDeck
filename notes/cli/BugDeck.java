import com.bugtraqapps.bugdeck.core.*;
import com.bugtraqapps.bugdeck.dork.*;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * BugDeck CLI - the same detection core that ships in the Android app, runnable
 * from a terminal with no Android SDK involved.
 *
 * <p>Exit codes are meaningful so this can be used in a pipeline:
 * <pre>
 *   0  ran, nothing vulnerable
 *   1  at least one VULNERABLE
 *   2  bad usage / bad arguments
 *   3  results were inconclusive (blocked or skipped, no verdict)
 * </pre>
 */
public class BugDeck {

    static PrintStream out;

    static final int EXIT_CLEAN     = 0;
    static final int EXIT_FOUND     = 1;
    static final int EXIT_USAGE     = 2;
    static final int EXIT_INCONCLUSIVE = 3;

    // ---- shared settings, set from flags --------------------------------

    static int timeoutMs = 8000;
    static String ua = Fetcher.DEFAULT_UA;

    public static void main(String[] args) throws Exception {
        // force UTF-8 so payload output survives a non-UTF8 locale
        out = new PrintStream(System.out, true, "UTF-8");
        if (args.length == 0) { usage(); System.exit(EXIT_USAGE); }

        int code;
        try {
            code = dispatch(args);
        } catch (UsageError e) {
            out.println("error: " + e.getMessage());
            usage();
            code = EXIT_USAGE;
        }
        out.flush();
        System.exit(code);
    }

    static int dispatch(String[] args) throws UsageError, InterruptedException {
        String cmd = args[0];
        if (cmd.equals("sqli"))          return cmdSqli(args);
        if (cmd.equals("admin"))         return cmdAdmin(args);
        if (cmd.equals("crawl"))         return cmdCrawl(args);
        if (cmd.equals("dork"))          return cmdDork(args);
        if (cmd.equals("wordlists"))     { cmdWordlists(); return EXIT_CLEAN; }
        if (cmd.equals("payloads"))      { cmdPayloads(args); return EXIT_CLEAN; }
        if (cmd.equals("help") || cmd.equals("-h") || cmd.equals("--help")) {
            usage();
            return EXIT_CLEAN;
        }
        throw new UsageError("unknown command: " + cmd);
    }

    /** Thrown for anything the user could fix by retyping. */
    static class UsageError extends RuntimeException {
        UsageError(String m) { super(m); }
    }

    // ---- flag parsing ---------------------------------------------------

    /**
     * Split {@code args[from..]} into positional values and flags.
     *
     * <p>Written by hand because the previous version walked the array with
     * {@code i < a.length - 1}, so a trailing flag silently swallowed the last
     * positional and {@code -t 5 url} put "url" where a number was expected.
     */
    static final class Flags {
        final List<String> positional = new ArrayList<>();
        final Map<String, String> values = new LinkedHashMap<>();
        final List<String> unknown = new ArrayList<>();

        boolean has(String k) { return values.containsKey(k); }
        String str(String k, String dflt) {
            String v = values.get(k);
            return (v == null || v.isEmpty()) ? dflt : v;
        }
        int integer(String k, int dflt) {
            String v = values.get(k);
            if (v == null || v.isEmpty()) return dflt;
            try { return Integer.parseInt(v.trim()); }
            catch (NumberFormatException e) {
                throw new UsageError("-" + k + " needs a whole number, got: " + v);
            }
        }
        boolean flag(String k) {
            return values.containsKey(k) && !values.get(k).equals("false");
        }
    }

    /** Flags that take a value; everything else is a boolean switch. */
    static final Set<String> VALUE_FLAGS = new HashSet<>(Arrays.asList(
        "-t", "-ua", "-w", "-n", "-d", "-p", "-k", "-cx", "-m", "-o", "-i",
        "--pages", "--max-pages", "--depth", "--timeout", "--threads",
        "--crawl-depth", "--crawl-pages"
    ));

    static Flags parse(String[] a, int from) {
        Flags f = new Flags();
        for (int i = from; i < a.length; i++) {
            String s = a[i];
            if (!s.startsWith("-") || s.equals("-")) { f.positional.add(s); continue; }

            String name = s, inline = null;
            int eq = s.indexOf('=');
            if (eq > 0) { name = s.substring(0, eq); inline = s.substring(eq + 1); }

            if (VALUE_FLAGS.contains(name)) {
                String v = inline;
                if (v == null) {
                    if (i + 1 >= a.length) {
                        throw new UsageError(name + " needs a value");
                    }
                    v = a[++i];
                }
                // last one wins, matching normal CLI expectation
                f.values.put(name, v);
            } else {
                f.values.put(name, inline == null ? "true" : inline);
            }
        }
        return f;
    }

    static void applyGlobals(Flags f) {
        int t = f.integer("-t", f.integer("--timeout", timeoutMs / 1000));
        timeoutMs = Math.max(1, t) * 1000;
        ua = f.str("-ua", ua);
    }

    // ---- sqli -----------------------------------------------------------

    static int cmdSqli(String[] args) throws UsageError {
        Flags f = parse(args, 1);
        applyGlobals(f);

        List<String> urls = new ArrayList<>();
        // a URL may come from the command line or from a file
        String listFile = f.str("-i", null);
        if (listFile != null) {
            List<String> fromFile = readLines(listFile);
            if (fromFile == null) return EXIT_USAGE;
            urls.addAll(fromFile);
        }
        urls.addAll(f.positional);

        boolean crawlMode = f.flag("--crawl");
        int depth = f.integer("-d", 1);
        int maxPages = f.integer("--max-pages", f.integer("-m", 20));

        if (crawlMode) {
            if (urls.size() != 1) {
                throw new UsageError("--crawl takes exactly one seed url");
            }
            return crawlThenProbe(urls.get(0), depth, maxPages);
        }

        if (urls.isEmpty()) {
            throw new UsageError("give at least one url (or -i file.txt)");
        }

        out.println("BugDeck SQLi  (" + urls.size() + " target(s), timeout " + timeoutMs / 1000 + "s)");
        out.println("-".repeat(78));
        out.printf("%-15s %-6s %s%n", "VERDICT", "HTTP", "URL / EVIDENCE");

        SqlInjector inj = new SqlInjector(ua, timeoutMs);
        int vuln = 0, blocked = 0, skipped = 0, clean = 0;
        List<SqlInjector.Result> results = new ArrayList<>();

        for (String u : urls) {
            SqlInjector.Result r = inj.probe(u);
            results.add(r);
            String ev = (r.evidence == null || r.evidence.isEmpty()) ? "" : "   <- " + r.evidence;
            out.printf("%-15s %-6s %s%s%n", r.verdict, r.probeStatus, r.url, ev);
            switch (r.verdict) {
                case VULNERABLE: vuln++; break;
                case BLOCKED:    blocked++; break;
                case SKIPPED:    skipped++; break;
                default:         clean++;
            }
        }

        out.println("-".repeat(78));
        out.printf("vulnerable=%d  not_vulnerable=%d  blocked=%d  skipped=%d%n",
                vuln, clean, blocked, skipped);

        String jsonOut = f.str("-o", null);
        if (jsonOut != null) writeJson(jsonOut, "sqli", results);

        if (vuln > 0) return EXIT_FOUND;
        if (blocked > 0 || skipped > 0) return EXIT_INCONCLUSIVE;
        return EXIT_CLEAN;
    }

    /** Crawl a site, then differentially probe every URL discovered. */
    static int crawlThenProbe(String seed, int depth, int maxPages) {
        out.println("crawling " + seed + "  (depth " + depth + ", max " + maxPages + " pages)");
        Crawler c = new Crawler(seed, ua, timeoutMs);
        Crawler.Result cr = c.crawl(depth, maxPages, true);

        if (cr.failure != null) {
            out.println("crawl failed: " + cr.failure);
            return EXIT_INCONCLUSIVE;
        }
        if (!cr.failures.isEmpty()) {
            out.println(cr.failures.size() + " page(s) could not be read:");
            for (String s : cr.failures) out.println("   " + s);
        }
        out.println("discovered " + cr.urls.size() + " url(s) on " + cr.pagesFetched + " page(s)");
        out.println();

        if (cr.urls.isEmpty()) {
            out.println("nothing to probe.");
            return EXIT_INCONCLUSIVE;
        }

        // only URLs that look like they take input are worth a quote probe
        List<String> targets = new ArrayList<>();
        for (String u : cr.urls) {
            String lower = u.toLowerCase();
            if (lower.contains("?") || lower.matches(".*\\.(php|asp|aspx|jsp|do|action)(\\?.*)?$")) {
                targets.add(u);
            }
        }
        out.println("probing " + targets.size() + " url(s) that accept parameters");
        out.println("-".repeat(78));
        out.printf("%-15s %-6s %s%n", "VERDICT", "HTTP", "URL / EVIDENCE");

        SqlInjector inj = new SqlInjector(ua, timeoutMs);
        int vuln = 0, blocked = 0, skipped = 0, clean = 0;
        for (String u : targets) {
            SqlInjector.Result r = inj.probe(u);
            String ev = (r.evidence == null || r.evidence.isEmpty()) ? "" : "   <- " + r.evidence;
            out.printf("%-15s %-6s %s%s%n", r.verdict, r.probeStatus, r.url, ev);
            switch (r.verdict) {
                case VULNERABLE: vuln++; break;
                case BLOCKED:    blocked++; break;
                case SKIPPED:    skipped++; break;
                default:         clean++;
            }
        }
        out.println("-".repeat(78));
        out.printf("vulnerable=%d  not_vulnerable=%d  blocked=%d  skipped=%d%n",
                vuln, clean, blocked, skipped);
        if (vuln > 0) return EXIT_FOUND;
        if (blocked > 0 || skipped > 0) return EXIT_INCONCLUSIVE;
        return EXIT_CLEAN;
    }

    // ---- admin ----------------------------------------------------------

    static int cmdAdmin(String[] args) throws UsageError, InterruptedException {
        Flags f = parse(args, 1);
        applyGlobals(f);
        if (f.positional.isEmpty()) {
            throw new UsageError("admin <baseUrl> -w <wordlist> [-n N] [-t sec]");
        }
        String base = f.positional.get(0);
        String wlName = f.str("-w", "mix1");
        int limit = f.integer("-n", 0);
        int threads = f.integer("--threads", 8);
        boolean follow = f.flag("--follow-redirects");

        List<String> entries = loadWordlist(wlName);
        if (entries == null) return EXIT_USAGE;

        if (limit > 0 && entries.size() > limit) entries = entries.subList(0, limit);

        AdminFinder af = new AdminFinder(ua, timeoutMs, threads);
        af.setFollowRedirects(follow);

        long started = System.currentTimeMillis();
        List<AdminFinder.Hit> hits = af.scan(base, entries);
        long ms = System.currentTimeMillis() - started;

        out.println("BugDeck Admin Finder");
        out.println("target : " + base);
        out.println("list   : " + wlName + " (" + entries.size() + " entries"
                + (follow ? ", following redirects" : "") + ")");
        out.println("-".repeat(78));
        for (AdminFinder.Hit h : hits) {
            if (h.verdict != Verdict.VULNERABLE) continue;
            out.printf("VULNERABLE  %-6s %-45s (%s)%n", h.status, h.url, h.note);
        }

        int found = 0, redir = 0, skip = 0, notFound = 0;
        for (AdminFinder.Hit h : hits) {
            switch (h.verdict) {
                case VULNERABLE: found++; break;
                case BLOCKED:    redir++; break;
                case SKIPPED:    skip++; break;
                default:         notFound++;
            }
        }

        out.println("-".repeat(78));
        out.printf("checked=%d  found=%d  redirect/blocked=%d  skipped=%d  not_found=%d  (%dms)%n",
                hits.size(), found, redir, skip, notFound, ms);
        if (found == 0) out.println("no admin panel found.");
        if (skip > 0) out.println("note: " + skip + " request(s) failed; those are NOT clean results.");

        return found > 0 ? EXIT_FOUND : (redir > 0 || skip > 0 ? EXIT_INCONCLUSIVE : EXIT_CLEAN);
    }

    // ---- dork -----------------------------------------------------------

    static int cmdDork(String[] args) throws UsageError {
        Flags f = parse(args, 1);
        applyGlobals(f);

        String providerId = f.str("-p", "brave");
        DorkProvider provider = DorkProviders.byId(providerId);
        if (provider == null) {
            out.println("no provider '" + providerId + "'.");
            out.println("available: " + providerIds());
            return EXIT_USAGE;
        }

        String dork = f.positional.isEmpty() ? "" : String.join(" ", f.positional);
        String domain = f.str("-d", "");
        String key = f.str("-k", System.getenv(envKeyFor(providerId)));
        String cx = f.str("-cx", System.getenv("GOOGLE_CSE_ID"));

        if (provider instanceof DorkProviders.GoogleCse && cx != null && !cx.isEmpty()) {
            DorkProviders.CseIdHolder.cx = cx;
        }

        if (dork.trim().isEmpty() && domain.trim().isEmpty()) {
            throw new UsageError("give a dork expression and/or -d <domain>");
        }

        int pages = f.integer("--pages", 1);

        out.println("BugDeck Dork  (" + provider.title() + ")");
        out.println("query  : " + (dork.isEmpty() ? "-" : dork)
                + (domain.isEmpty() ? "" : "  site:" + domain));
        if (provider.requiresKey()) {
            out.println("key    : " + (key == null || key.isEmpty() ? "(not set)" : "*** set"));
        }
        out.println("-".repeat(78));

        if (provider.requiresKey()) {
            if (key == null || key.trim().isEmpty()) {
                out.println("cannot search: " + provider.keyHint());
                return EXIT_USAGE;
            }
            if (provider instanceof DorkProviders.GoogleCse
                    && (cx == null || cx.trim().isEmpty())) {
                out.println("cannot search: " + provider.keyHint());
                return EXIT_USAGE;
            }
        }

        DorkSearch s = new DorkSearch(ua, timeoutMs);
        DorkSearch.Result r = pages > 1
            ? s.searchAllPages(provider, dork, domain, key, pages, null)
            : s.search(provider, dork, domain, 1, key, null);

        if (r.note != null) out.println("note: " + r.note);
        for (String u : r.urls) out.println("  " + u);

        out.println("-".repeat(78));
        if (r.blocked) out.println("BLOCKED - the engine returned a challenge, not results");
        out.println(r.urls.size() + " result(s), http " + r.httpStatus);

        String jsonOut = f.str("-o", null);
        if (jsonOut != null) {
            List<String> urls = r.urls;
            writeJson(jsonOut, "dork", urls);
        }

        if (r.failed()) return EXIT_INCONCLUSIVE;
        return EXIT_CLEAN;
    }

    /** Env var each provider reads its credential from. */
    static String envKeyFor(String providerId) {
        if ("brave".equals(providerId))  return "BRAVE_API_KEY";
        if ("google".equals(providerId)) return "GOOGLE_API_KEY";
        return providerId.toUpperCase() + "_API_KEY";
    }

    static String providerIds() {
        StringBuilder sb = new StringBuilder();
        for (DorkProvider p : DorkProviders.available()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.id());
            if (!p.requiresKey()) sb.append('*');
        }
        return sb.toString() + "     (* = no API key needed)";
    }

    // ---- crawl ----------------------------------------------------------

    /** List the URLs a crawl discovers. Probing them is `sqli --crawl`. */
    static int cmdCrawl(String[] args) throws UsageError {
        Flags f = parse(args, 1);
        applyGlobals(f);
        if (f.positional.isEmpty()) throw new UsageError("crawl <seedUrl> [-d depth] [--max-pages N]");

        String seed = f.positional.get(0);
        int depth = f.integer("-d", f.integer("--depth", 1));
        int maxPages = f.integer("--max-pages", 20);
        boolean crossHost = f.flag("--cross-host");

        out.println("BugDeck Crawl  " + seed);
        out.println("depth " + depth + ", max " + maxPages + " pages, "
                + (crossHost ? "following off-site links" : "same host only"));
        out.println("-".repeat(78));

        Crawler c = new Crawler(seed, ua, timeoutMs);
        Crawler.Result r = c.crawl(depth, maxPages, !crossHost);

        if (r.failure != null) {
            out.println("crawl failed: " + r.failure);
            return EXIT_INCONCLUSIVE;
        }
        for (String u : r.urls) out.println(u);
        out.println("-".repeat(78));
        out.println(r.urls.size() + " url(s) from " + r.pagesFetched + " page(s)");
        if (!r.failures.isEmpty()) {
            out.println(r.failures.size() + " skipped:");
            for (String s : r.failures) out.println("   " + s);
        }
        return r.urls.isEmpty() ? EXIT_INCONCLUSIVE : EXIT_CLEAN;
    }

    // ---- assets ---------------------------------------------------------

    static Path assetsDir() {
        String[] tries = {
            System.getProperty("bugdeck.assets"),
            "bugdeck/assets/wordlists",
            "../bugdeck/assets/wordlists",
            "assets/wordlists",
            "bugdeck/assets/wordlists"
        };
        for (String t : tries) {
            if (t != null && Files.isDirectory(Paths.get(t))) return Paths.get(t);
        }
        return Paths.get("bugdeck/assets/wordlists");
    }

    static final Path ASSETS = assetsDir();

    static List<String> wordlistNames() {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(ASSETS, "*.txt")) {
            for (Path p : ds) names.add(p.getFileName().toString().replace(".txt", ""));
        } catch (Exception ignored) { }
        Collections.sort(names);
        return names;
    }

    static List<String> loadWordlist(String name) {
        Path p = ASSETS.resolve(name + ".txt");
        if (!Files.exists(p)) {
            out.println("no such wordlist: " + name);
            out.println("available: " + String.join(", ", wordlistNames()));
            return null;
        }
        List<String> vals = new ArrayList<>();
        for (String l : readLinesOrEmpty(p)) {
            String t = l.trim();
            if (!t.isEmpty()) vals.add(t);
        }
        return vals;
    }

    static List<String> readLines(String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            out.println("no such file: " + path);
            return null;
        }
        return readLinesOrEmpty(p);
    }

    static List<String> readLinesOrEmpty(Path p) {
        List<String> out2 = new ArrayList<>();
        try {
            for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) out2.add(l);
        } catch (Exception e) {
            out.println("cannot read " + p + ": " + e.getMessage());
        }
        return out2;
    }

    static void cmdWordlists() {
        out.println("bundled wordlists in " + ASSETS.toAbsolutePath());
        for (String n : wordlistNames()) {
            List<String> l = readLinesOrEmpty(ASSETS.resolve(n + ".txt"));
            long ext = 0;
            for (String s : l) if (s.contains("%EXT%")) ext++;
            out.printf("  %-8s %5d entries%s%n", n, l.size(),
                    ext > 0 ? "  (" + ext + " with %EXT%, auto-expanded)" : "");
        }
    }

    static void cmdPayloads(String[] args) {
        Path p = ASSETS.getParent().resolve("payloads.json");
        if (!Files.exists(p)) {
            out.println("payloads.json not found at " + p.toAbsolutePath());
            return;
        }
        boolean brief = false;
        String filter = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--list")) brief = true;
            else if (args[i].startsWith("-")) continue;
            else if (filter == null) filter = args[i];
        }

        String json;
        try {
            json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (Exception e) {
            out.println("cannot read " + p + ": " + e.getMessage());
            return;
        }

        // the file nests each category as {title, source, payloads:[...]};
        // walk it with the same parser the app uses rather than by regex
        List<String> cats = categories(json);
        int total = 0, shown = 0;
        for (String cat : cats) {
            int braceAt = objectStartFor(json, cat);
            if (braceAt < 0) continue;
            List<String> vals = arrayFor(json, braceAt, "payloads");
            String title = stringFor(json, braceAt, "title");
            if (vals.isEmpty()) continue;
            if (filter != null && !cat.equals(filter)) continue;
            total += vals.size();
            shown++;
            if (brief) {
                out.printf("  %-18s %4d  %s%n", cat, vals.size(), title);
            } else {
                out.println("== " + cat + "  (" + vals.size() + ")  " + title);
                for (String v : vals) out.println("   " + v.replace("\n", "\\n"));
                out.println();
            }
        }
        if (brief) {
            out.println("\n" + shown + " categories, " + total + " payloads");
        } else if (shown == 0) {
            out.println("no category '" + filter + "'.");
            out.println("use: payloads --list   to see all categories");
        }
    }

    // ---- tiny JSON emitters (no dependency) -----------------------------

    /** Category keys at the top level of payloads.json. */
    static List<String> categories(String json) {
        List<String> out2 = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"([a-z0-9_]+)\"\\s*:\\s*\\{").matcher(json);
        while (m.find()) out2.add(m.group(1));
        return out2;
    }

    /** Index of the '{' that opens the object for a top-level key, or -1. */
    static int objectStartFor(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\\{")
            .matcher(json);
        return m.find() ? m.end() - 1 : -1;
    }

    /** Read a string[] field from the object starting at {@code braceAt}. */
    static List<String> arrayFor(String json, int braceAt, String field) {
        List<String> out2 = new ArrayList<>();
        int fieldAt = json.indexOf("\"" + field + "\"", braceAt);
        if (fieldAt < 0) return out2;
        int lb = json.indexOf('[', fieldAt);
        if (lb < 0) return out2;
        int rb = matchBracket(json, lb);
        if (rb < 0) return out2;

        String body = json.substring(lb + 1, rb);
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c != '"') { i++; continue; }
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < body.length()) {
                char d = body.charAt(i++);
                if (d == '"') break;
                if (d != '\\') { sb.append(d); continue; }
                if (i >= body.length()) break;
                char e = body.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        if (i + 4 > body.length()) { i = body.length(); break; }
                        try {
                            sb.append((char) Integer.parseInt(body.substring(i, i + 4), 16));
                        } catch (Exception ignored) { }
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            }
            out2.add(sb.toString());
        }
        return out2;
    }

    /** Read a string field from the object starting at {@code braceAt}. */
    static String stringFor(String json, int braceAt, String field) {
        int at = json.indexOf("\"" + field + "\"", braceAt);
        if (at < 0) return "";
        int q1 = json.indexOf('"', at + field.length() + 2);
        if (q1 < 0) return "";
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return json.substring(q1 + 1, q2);
    }

    static int matchBracket(String s, int openAt) {
        int depth = 0;
        boolean inStr = false;
        for (int i = openAt; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') i++;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ---- json output ----------------------------------------------------

    static void writeJson(String path, String kind, Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"tool\": \"bugdeck\",\n  \"kind\": \"").append(kind).append("\",\n  \"results\": [");
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            for (int i = 0; i < list.size(); i++) {
                Object o = list.get(i);
                sb.append(i == 0 ? "\n" : ",\n");
                sb.append("    ");
                if (o instanceof SqlInjector.Result) {
                    SqlInjector.Result r = (SqlInjector.Result) o;
                    sb.append("{\"url\": ").append(q(r.url))
                      .append(", \"verdict\": ").append(q(r.verdict.toString()))
                      .append(", \"http\": ").append(r.probeStatus)
                      .append(", \"evidence\": ").append(q(r.evidence)).append("}");
                } else {
                    sb.append("{\"url\": ").append(q(String.valueOf(o))).append("}");
                }
            }
            if (!list.isEmpty()) sb.append("\n  ");
        }
        sb.append("]\n}\n");
        try {
            Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
            out.println("wrote " + path);
        } catch (Exception e) {
            out.println("cannot write " + path + ": " + e.getMessage());
        }
    }

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ---- usage ----------------------------------------------------------

    static void usage() {
        out.println("BugDeck - fixed web scanner core (from BugTraq Droidbug + DH HackBar)");
        out.println();
        out.println("USAGE");
        out.println("  BugDeck sqli <url>... [-i list.txt] [--crawl] [opts]");
        out.println("  BugDeck admin <baseUrl> -w <list> [-n N] [--threads N] [opts]");
        out.println("  BugDeck dork <expr> [-d domain] -p bing|ddg|mojeek|startpage|searxng|brave|google");
        out.println("  BugDeck crawl <seedUrl> [-d depth] [--max-pages N]   (list urls only)");
        out.println("  BugDeck wordlists | BugDeck payloads [--list] [category]");
        out.println();
        out.println("GLOBAL OPTIONS");
        out.println("  -t SEC        per-request timeout (default 8)");
        out.println("  -ua STRING    User-Agent to send");
        out.println("  -o FILE       write JSON results to FILE");
        out.println();
        out.println("SQLI / ADMIN OPTIONS");
        out.println("  -w LIST       wordlist name (see: BugDeck wordlists)");
        out.println("  -n N          stop after N entries");
        out.println("  --threads N   admin concurrency (default 8)");
        out.println("  --follow-redirects   follow 3xx instead of reporting it");
        out.println("  --crawl       crawl the seed site, then probe what it finds");
        out.println("  -d N          crawl depth (default 1)");
        out.println("  --max-pages N crawl page cap (default 20)");
        out.println();
        out.println("DORK OPTIONS");
        out.println("  -p ID         " + providerIds());
        out.println("  -k KEY        API key (credentialed providers only)");
        out.println("  -cx ID        Google search-engine id (or env GOOGLE_CSE_ID)");
        out.println("                env: BRAVE_API_KEY / GOOGLE_API_KEY / SEARXNG_URL");
        out.println("  --pages N     walk up to N result pages");
        out.println();
        out.println("EXIT CODES");
        out.println("  0  ran, nothing vulnerable");
        out.println("  1  at least one VULNERABLE");
        out.println("  2  bad usage / missing configuration");
        out.println("  3  inconclusive (blocked or skipped, no verdict)");
        out.println();
        out.println("VERDICTS");
        out.println("  VULNERABLE     confirmed by differential probe");
        out.println("  NOT_VULNERABLE no DB error the clean URL did not also have");
        out.println("  BLOCKED        403/429/WAF challenge -> inconclusive, not clean");
        out.println("  SKIPPED        timeout/DNS/TLS -> never reported as clean");
        out.println();
        out.println("EXAMPLES");
        out.println("  BugDeck sqli \"http://site/page.php?id=1\"");
        out.println("  BugDeck sqli http://site --crawl -d 2 --max-pages 50");
        out.println("  BugDeck admin http://site -w mix1 -n 60 --threads 12");
        out.println("  BugDeck dork 'inurl:admin' -p brave -k YOUR_KEY --pages 3");
    }
}
