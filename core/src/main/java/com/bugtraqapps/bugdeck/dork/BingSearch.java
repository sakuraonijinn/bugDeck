package com.bugtraqapps.bugdeck.dork;

import com.bugtraqapps.bugdeck.core.HtmlLinks;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bing, parsed from its public HTML search page.
 *
 * <p>Bing is the one keyless engine that reliably returns parseable markup from
 * a datacenter IP, so it is the default for the keyless dork path.
 *
 * <p>Its one real quirk: every outbound link is a redirect of the form
 * {@code https://www.bing.com/ck/a?...&u=a1<base64url>&ntb=1}, where the real
 * destination is Base64 in {@code u=a1}. Harvesting the href directly yields 34
 * {@code bing.com/ck/} decoys per page and zero real results, so the parameter
 * has to be decoded. The value uses the URL-safe alphabet and is frequently
 * unpadded, so standard Base64 decoding fails on roughly half of them.
 */
public final class BingSearch {

    private BingSearch() {}

    private static final Pattern RESULT_BLOCK =
        Pattern.compile("(?is)<li class=\"b_algo\".*?</li>");
    private static final Pattern CK_LINK =
        Pattern.compile("(https?://(?:www\\.)?bing\\.com/ck/a\\?[^\\s\"'>]+)");
    private static final Pattern U_PARAM =
        Pattern.compile("[?&]u=a1([A-Za-z0-9_\\-]+)");
    private static final Pattern HREF =
        Pattern.compile("(?i)href=\"([^\"]+)\"");

    /** Hosts that are Bing chrome, not results. */
    private static final String[] BING_CHROME = {
        "bing.com/images", "bing.com/videos", "bing.com/maps", "bing.com/news",
        "bing.com/settings", "bing.com/ck/", "go.microsoft.com", "bing.com/flights",
        "bing.com/copilotsearch", "bing.com/shopping", "bing.com/translator"
    };

    /**
     * Parse a Bing results page.
     *
     * <p>Prefers decoding the {@code b_algo} blocks; falls back to a whole-page
     * href harvest if the template changed, so a layout change degrades to "some
     * results" rather than "no results".
     */
    public static List<String> parse(String html) {
        Set<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();

        Matcher blocks = RESULT_BLOCK.matcher(html);
        while (blocks.find()) {
            collectFromBlock(blocks.group(), out);
        }
        if (out.isEmpty()) {
            // template drift: harvest every href and unwrap whatever we can
            Matcher h = HREF.matcher(html);
            while (h.find()) addCandidate(out, h.group(1));
        }
        return new ArrayList<>(out);
    }

    /** Pull result links out of one {@code <li class="b_algo">} block. */
    private static void collectFromBlock(String block, Set<String> out) {
        Matcher h = HREF.matcher(block);
        boolean sawAny = false;
        while (h.find()) {
            String raw = HtmlLinks.decodeEntities(h.group(1).trim());
            // unwrap /ck/a -> real destination
            Matcher u = U_PARAM.matcher(raw);
            if (u.find()) {
                String real = decodeCkParam("a1" + u.group(1));
                if (real != null && !real.isEmpty()) {
                    addCandidate(out, real);
                    sawAny = true;
                }
                continue;
            }
            if (isChrome(raw)) continue;
            addCandidate(out, raw);
            sawAny = true;
        }
        // a block that yielded nothing usable is not a result
        if (!sawAny) { /* nothing to add */ }
    }

    /**
     * Decode Bing's {@code u=a1} parameter.
     *
     * <p>Handles the URL-safe alphabet ({@code -} and {@code _}), missing
     * padding, and the {@code a1} prefix the caller may have left on.
     *
     * @return the destination url, or the input unchanged when it is not encoded
     */
    public static String decodeCkParam(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return v;

        // strip the a1 prefix if present
        if (v.startsWith("a1")) v = v.substring(2);
        if (v.isEmpty()) return "";

        // if it decoded cleanly as raw base64, keep it; otherwise pass through
        String candidate = v.replace('-', '+').replace('_', '/');
        int pad = candidate.length() % 4;
        if (pad == 2) candidate += "==";
        else if (pad == 3) candidate += "=";
        else if (pad == 1) {
            // cannot be valid base64 length
            return v;
        }

        try {
            byte[] raw = Base64.getDecoder().decode(candidate);
            String s = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            if (s.startsWith("http://") || s.startsWith("https://")) return s;
            return v;   // not a url: return as-is
        } catch (IllegalArgumentException e) {
            return v;
        }
    }

    private static void addCandidate(Set<String> out, String url) {
        if (url == null) return;
        String u = url.trim();
        if (u.startsWith("//")) u = "https:" + u;
        if (!u.startsWith("http://") && !u.startsWith("https://")) return;
        if (isChrome(u)) return;
        out.add(u);
    }

    private static boolean isChrome(String url) {
        String h = url.toLowerCase(java.util.Locale.ROOT);
        for (String c : BING_CHROME) if (h.contains(c)) return true;
        // r.bing.com and friends are asset hosts
        if (h.contains("r.bing.com") || h.contains("th.bing.com")) return true;
        return false;
    }
}
