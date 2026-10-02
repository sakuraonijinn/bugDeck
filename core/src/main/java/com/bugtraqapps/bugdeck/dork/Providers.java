package com.bugtraqapps.bugdeck.dork;

import com.bugtraqapps.bugdeck.core.HtmlLinks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyless search providers, parsed from their public HTML.
 *
 * <p>Empirical reality, measured from a datacenter IP against the live engines:
 * DuckDuckGo, Mojeek, Startpage, Ecosia and several SearXNG instances all serve
 * an anti-bot page instead of results, and Brave's web UI is JS-rendered. That is
 * not a bug in this code, it is how those engines respond to automated clients.
 *
 * <p>So the honest design is:
 * <ul>
 *   <li>Bing works and is wired up properly.</li>
 *   <li>The others are implemented and tested against captured markup, and report
 *       a clear {@code BLOCKED} rather than a silent "0 results" when the engine
 *       serves a challenge. On a residential IP some of them will work; from a
 *       datacentre they will not, and pretending otherwise would be the exact
 *       false-negative behaviour this project exists to remove.</li>
 * </ul>
 */
public final class Providers {

    private Providers() {}

    // ---- anti-bot detection ----------------------------------------------

    private static final String[] BLOCK_MARKERS = {
        "anomaly detected", "unusual traffic", "are you a robot", "not a robot",
        "captcha", "challenge-form", "checking your browser", "just a moment",
        "enable javascript and cookies", "cf-browser-verification",
        "px-captcha", "unusual activity", "bot detection"
    };

    /**
     * Detect an anti-bot interstitial.
     *
     * @return a human reason, or null when the body looks like real results
     */
    public static String detectBlock(String body) {
        if (body == null || body.isEmpty()) return null;
        String h = body.toLowerCase(Locale.ROOT);
        for (String m : BLOCK_MARKERS) {
            if (h.contains(m)) {
                return "served an anti-bot page (" + m + "); this engine is "
                     + "blocking automated clients from this network";
            }
        }
        return null;
    }

    // ---- per-engine parsers ----------------------------------------------

    private static final Pattern DDG_RESULT =
        Pattern.compile("(?is)<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"");
    private static final Pattern DDG_RESULT_ALT =
        Pattern.compile("(?is)<a[^>]+href=\"([^\"]+)\"[^>]*class=\"[^\"]*result__a[^\"]*\"");

    /** DuckDuckGo html/lite: {@code class="result__a"} anchors, /l/?uddg= wrapped. */
    public static List<String> parseDuckDuckGo(String html) {
        Set<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();
        Matcher m = DDG_RESULT.matcher(html);
        while (m.find()) addUrl(out, m.group(1));
        Matcher m2 = DDG_RESULT_ALT.matcher(html);
        while (m2.find()) addUrl(out, m2.group(1));
        return new ArrayList<>(out);
    }

    private static final Pattern MOJEEK_RESULT =
        Pattern.compile("(?is)<a[^>]+class=\"[^\"]*\\bob\\b[^\"]*\"[^>]*href=\"([^\"]+)\"");

    /** Mojeek: {@code <a class="ob" href=...>} inside ul.results-standard. */
    public static List<String> parseMojeek(String html) {
        Set<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();
        Matcher m = MOJEEK_RESULT.matcher(html);
        while (m.find()) addUrl(out, m.group(1));
        // fall back to a generic harvest if the template moved
        if (out.isEmpty()) out.addAll(HtmlLinks.extract(html));
        return new ArrayList<>(out);
    }

    private static final Pattern STARTPAGE_RESULT =
        Pattern.compile("(?is)<a[^>]+class=\"[^\"]*result-link[^\"]*\"[^>]*href=\"([^\"]+)\"");
    private static final Pattern STARTPAGE_OLD =
        Pattern.compile("(?is)<a[^>]+class=\"[^\"]*w-gl__result-title[^\"]*\"[^>]*href=\"([^\"]+)\"");

    /** Startpage: POST to /sp/search, results in a.result-link. */
    public static List<String> parseStartpage(String html) {
        Set<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();
        Matcher m = STARTPAGE_RESULT.matcher(html);
        while (m.find()) addUrl(out, m.group(1));
        Matcher m2 = STARTPAGE_OLD.matcher(html);
        while (m2.find()) addUrl(out, m2.group(1));
        if (out.isEmpty()) out.addAll(HtmlLinks.extract(html));
        return new ArrayList<>(out);
    }

    /**
     * Mojeek also runs a JSON API, but it needs a key, so this stays unused for
     * the keyless path and exists only for completeness of the parser family.
     */
    public static List<String> parseGenericJson(String body) {
        return JsonLite.harvest(body);
    }

    // ---- shared helpers ---------------------------------------------------

    private static void addUrl(Set<String> out, String raw) {
        if (raw == null) return;
        String u = HtmlLinks.decodeEntities(HtmlLinks.unwrap(raw.trim()));
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("//")) return;
        out.add(u);
    }
}
