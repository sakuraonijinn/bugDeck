package com.bugtraqapps.bugdeck.dork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader, enough to pull URLs out of search API responses.
 *
 * <p>Exists so the core has no third-party dependency and no Android
 * dependency: org.json ships with Android but not with a desktop JVM, and
 * adding it here would have meant the CLI and the app disagreeing about
 * behaviour on the same input.
 *
 * <p>Strict about structure, forgiving about content. Returns Map/List/String
 * leaves, and a failed parse yields an empty map rather than an exception,
 * because a search backend changing its schema should degrade to "no results
 * this page", not crash a scan mid-run.
 */
final class JsonLite {

    private final String s;
    private int i;

    private JsonLite(String s) { this.s = s; }

    /** Parse a JSON object. Never throws; returns empty on any error. */
    static Map<String, Object> parseObject(String json) {
        if (json == null) return new LinkedHashMap<String, Object>();
        try {
            JsonLite p = new JsonLite(json);
            p.ws();
            if (p.peek() != '{') return new LinkedHashMap<String, Object>();
            Object v = p.value();
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                return m;
            }
            return new LinkedHashMap<String, Object>();
        } catch (RuntimeException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    // ---- provider-specific extractors ----------------------------------

    /**
     * Google Programmable Search: {@code items[].link}, or {@code pagemap}
     * when the response is the older CSE format.
     */
    static List<String> itemsUrls(String body) {
        List<String> out = new ArrayList<>();
        Map<String, Object> root = parseObject(body);

        List<Object> items = asList(root.get("items"));
        for (Object o : items) {
            String u = firstString(asMap(o), "link", "url");
            if (u != null) out.add(u);
        }
        if (!out.isEmpty()) return out;

        // older shape: pagemap.result[].{url,unescapedUrl}
        Map<String, Object> pagemap = asMap(root.get("pagemap"));
        for (Object byPage : asList(pagemap.get("result"))) {
            String u = firstString(asMap(byPage), "unescapedUrl", "url");
            if (u != null) out.add(u);
        }
        return out;
    }

    /**
     * Brave Search: {@code web.results[].url}.
     */
    static List<String> webResultUrls(String body) {
        List<String> out = new ArrayList<>();
        Map<String, Object> root = parseObject(body);
        Map<String, Object> web = asMap(root.get("web"));
        for (Object o : asList(web.get("results"))) {
            String u = firstString(asMap(o), "url");
            if (u != null) out.add(u);
        }
        return out;
    }

    /**
     * Generic harvest: walk the whole tree and collect anything under a
     * url-ish key. Used by keyless providers that return JSON we do not have a
     * schema for.
     */
    static List<String> harvest(String body) {
        List<String> out = new ArrayList<>();
        walk(parseObject(body), out, 0);
        return out;
    }

    private static void walk(Object node, List<String> out, int depth) {
        if (depth > 12 || out.size() > 500) return;      // bound pathological input
        if (node instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (v instanceof String
                        && (k.equals("url") || k.equals("link") || k.endsWith("Url"))
                        && ((String) v).startsWith("http")) {
                    if (!out.contains(v)) out.add((String) v);
                } else walk(v, out, depth + 1);
            }
        } else if (node instanceof List) {
            for (Object o : (List<?>) node) walk(o, out, depth + 1);
        }
    }

    // ---- accessors ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o
                                 : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return (o instanceof List) ? (List<Object>) o : new ArrayList<Object>();
    }

    /** First non-empty string among the given keys, else null. */
    private static String firstString(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        }
        return null;
    }

    // ---- scanner -------------------------------------------------------

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        ws();
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private Object value() {
        char c = peek();
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true");  return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null");  return null;
            default:  return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;                                   // '{'
        if (peek() == '}') { i++; return m; }
        while (i < s.length()) {
            if (peek() != '"') break;
            String k = string();
            if (peek() != ':') break;
            i++;
            m.put(k, value());
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; break; }
            break;
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;                                   // '['
        if (peek() == ']') { i++; return l; }
        while (i < s.length()) {
            l.add(value());
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; break; }
            break;
        }
        return l;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;                                   // opening quote
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) { i = s.length(); break; }
                    try {
                        sb.append((char) Integer.parseInt(
                            s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ignored) { }
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+'
                    || c == '.' || c == 'e' || c == 'E') i++;
            else break;
        }
        String t = s.substring(start, i);
        if (t.isEmpty()) { i++; return null; }   // skip junk char
        try {
            if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                return Long.valueOf(t);
            }
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return t;                              // keep raw text, do not crash
        }
    }

    private void expect(String lit) {
        if (s.startsWith(lit, i)) i += lit.length();
        else throw new IllegalStateException("bad literal at " + i);
    }
}
