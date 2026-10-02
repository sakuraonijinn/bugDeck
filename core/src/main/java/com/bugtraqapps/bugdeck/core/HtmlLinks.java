package com.bugtraqapps.bugdeck.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal HTML link harvesting, so neither the crawler nor the dork engine needs
 * a jsoup dependency.
 *
 * <p>Relative links matter and are easy to get wrong: {@code href="/admin.php"}
 * is the single most common form on a real site. {@link #extract(String)} keeps
 * absolute-only because a dork's result list is all absolute anyway, while
 * {@link #extract(String, boolean)} with {@code allowRelative} returns raw
 * candidates for a caller that will resolve them against a base URL.
 */
public final class HtmlLinks {

    private HtmlLinks() {}

    private static final Pattern HREF =
        Pattern.compile("(?i)<a[^>]+href\\s*=\\s*[\"']([^\"']+)[\"']");
    // DDG wraps as //duckduckgo.com/l/?uddg=..., /l/?uddg=..., or the full host
    private static final Pattern DDG_REDIRECT =
        Pattern.compile("(?i)^(?:https?:)?//(?:[a-z0-9-]+\\.)*duckduckgo\\.com/l/\\?uddg=([^&]+)");

    /** Absolute http(s) links only. */
    public static List<String> extract(String html) {
        return extract(html, false);
    }

    /**
     * @param allowRelative when true, also return site-relative hrefs
     *                      ({@code /a}, {@code a/b}, {@code ../x}) unresolved
     */
    public static List<String> extract(String html, boolean allowRelative) {
        Set<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            String href = m.group(1).trim();
            if (href.isEmpty()) continue;
            href = unwrap(href);
            href = decodeEntities(href);
            if (allowRelative ? isCandidate(href) : isUsable(href)) out.add(href);
        }
        return new ArrayList<>(out);
    }

    /**
     * Peel search-engine redirect wrappers off an href.
     *
     * <p>Most engines do not link out directly. DuckDuckGo wraps every result as
     * {@code /l/?uddg=<encoded>}; following that would return the search page
     * again instead of the result, so the wrapper has to come off here or every
     * harvested "url" is a decoy.
     */
    public static String unwrap(String href) {
        if (href == null) return null;
        String h = href.trim();
        Matcher r = DDG_REDIRECT.matcher(h);
        if (r.find()) return urlDecode(r.group(1));
        return h;
    }

    /** Absolute http/https/protocol-relative, minus nav and assets. */
    private static boolean isUsable(String href) {
        String h = href.toLowerCase();
        if (hasBadScheme(h)) return false;
        if (isNoise(h)) return false;
        return h.startsWith("http://") || h.startsWith("https://") || h.startsWith("//");
    }

    /**
     * Candidate for a caller that will resolve it: rejects unusable schemes and
     * obvious noise, but keeps relative paths, which is the whole point.
     */
    private static boolean isCandidate(String href) {
        String h = href.toLowerCase();
        if (hasBadScheme(h)) return false;
        if (isNoise(h)) return false;
        // a bare fragment is not a page
        if (h.startsWith("#")) return false;
        return true;
    }

    private static boolean hasBadScheme(String h) {
        for (String s : NON_HTTP) if (h.startsWith(s)) return true;
        return false;
    }

    private static final String[] NON_HTTP = {
        "javascript:", "mailto:", "tel:", "sms:", "data:", "ftp:", "file:"
    };

    private static boolean isNoise(String h) {
        if (h.contains("/search?") || h.contains("/images?") || h.contains("duckduckgo.com/")
                || h.contains("google.com/recaptcha")) {
            return true;
        }
        return h.endsWith(".css") || h.endsWith(".js") || h.endsWith(".png")
            || h.endsWith(".svg") || h.endsWith(".jpg") || h.endsWith(".gif");
    }

    public static String decodeEntities(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    public static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
