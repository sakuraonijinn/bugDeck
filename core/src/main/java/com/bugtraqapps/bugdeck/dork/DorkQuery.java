package com.bugtraqapps.bugdeck.dork;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Turns a user-entered operator expression into a search query string.
 *
 * <p>Deliberately not a parser. Dork syntax is a flat space-separated list of
 * tokens where quoting matters only around phrases, so this does a single
 * pass that:
 * <ol>
 *   <li>honours quoted phrases verbatim (keeping the quotes, since every
 *       provider treats {@code "a b"} as a phrase and {@code a b} as two terms),</li>
 *   <li>upper-cases known operators so {@code site:} and {@code SITE:} behave the
 *       same,</li>
 *   <li>appends a {@code site:} restriction when the user gave one separately.</li>
 * </ol>
 *
 * <p>Anything unrecognised is passed through untouched. Refusing to parse is
 * safer than trying to be clever: a dork we do not understand is still a
 * dork the user meant.
 */
final class DorkQuery {

    private DorkQuery() {}

    /**
     * Operators that must keep their lowercase form. Anything starting with a
     * word followed by ':' is treated as an operator unless it is in here.
     */
    private static final String[] KNOWN_OPERATORS = {
        "site:", "inurl:", "intitle:", "filetype:", "ext:", "intext:",
        "allintext:", "allinurl:", "cache:", "link:", "related:", "before:",
        "after:", "language:", "define:", "info:", "numterms:", "offset:"
    };

    /**
     * Build the final query string.
     *
     * @param dork   raw operator expression, may be empty
     * @param domain optional site/domain restriction, may be empty/null
     * @return query string, or null if there is nothing to search for
     */
    static String build(String dork, String domain) {
        String q = normalize(dork);
        String site = normalizeDomain(domain);
        if (site == null && q == null) return null;

        StringBuilder sb = new StringBuilder();
        if (q != null) sb.append(q);
        if (site != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("site:").append(site);
        }
        return sb.toString();
    }

    /**
     * Normalise one expression: trim, collapse runs of whitespace, upper-case
     * known operator prefixes, and drop empties. Returns null when nothing is
     * left.
     */
    static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;

        // Re-emit token by token so quoted phrases stay intact.
        StringBuilder out = new StringBuilder();
        for (String tok : splitRespectingQuotes(s)) {
            if (tok.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(canonical(tok));
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * A domain restriction must look like a host, otherwise it is silently
     * appended as {@code site:} nonsense. Returns null when unusable.
     */
    private static String normalizeDomain(String raw) {
        if (raw == null) return null;
        String d = raw.trim();
        if (d.isEmpty()) return null;
        // strip a scheme and any path the user pasted in
        d = d.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        if (d.isEmpty()) return null;
        // must contain a dot and no spaces, else it is not a host
        if (d.indexOf('.') < 0 || d.indexOf(' ') >= 0) return null;
        return d;
    }

    /** Upper-case the prefix of known operators, leave the rest alone. */
    private static String canonical(String tok) {
        int colon = tok.indexOf(':');
        if (colon <= 0) return tok;              // no operator, or leading ':'
        String head = tok.substring(0, colon + 1);
        for (String op : KNOWN_OPERATORS) {
            if (op.equalsIgnoreCase(head)) return op + tok.substring(colon + 1);
        }
        return tok;                              // unknown: not our business
    }

    /**
     * Split on whitespace but keep {@code "quoted phrases"} as single tokens.
     * Quotes are retained: providers rely on them to mean phrase-match.
     */
    static java.util.List<String> splitRespectingQuotes(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') { inQuote = !inQuote; cur.append(c); }
            else if (!inQuote && Character.isWhitespace(c)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /**
     * Percent-encode parameters into a query string, preserving insertion order.
     *
     * <p>Hand-rolled rather than built from streams so the core stays on
     * {@code java.io} + {@code java.net} + {@code java.util.regex} only, which is
     * the subset Android supports at every API level this app targets.
     */
    static String encode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 missing", impossible);
        }
    }
}
