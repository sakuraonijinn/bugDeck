package com.bugtraqapps.bugdeck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signature matching for HTTP responses.
 *
 * <p>Replaces the original apps' {@code body.contains("mysql_fetch")} approach.
 * Two problems with that are fixed here:
 *
 * <ol>
 *   <li>The original matched bare generic strings ("Syntax error", "Query failed",
 *       "mysql_fetch") against whatever the server returned. Any page that mentions
 *       those words for unrelated reasons (WordPress, phpMyAdmin, a blog post about
 *       MySQL) reported "Vulnerable!" — this is the main source of false positives.</li>
 *   <li>No WAF / 403 handling, so a Cloudflare challenge page scored as
 *       "Not Vulnerable" when it was really "inconclusive".</li>
 * </ol>
 */
public final class Signature {

    private Signature() {}

    // ---- database error fingerprints -------------------------------------
    // Each pattern is deliberately specific: it requires the vendor's own
    // wording plus structural markers, not just a common word.

    private static final String[] DB_ERROR = {
        // MySQL
        "you have an error in your sql syntax",
        "warning.*mysql_",
        "mysql_fetch_(array|row|assoc|object)\\(\\)\\s*[:>]",
        "mysql_num_rows\\(\\)\\s*[:>]",
        "supplied argument is not a valid mysql",
        "mysqli?_sql_exception",
        "unclosed quotation mark after the character string",
        "com\\.mysql\\.jdbc\\.exceptions",
        // PostgreSQL
        "pg_(query|exec|fetch_)\\w*\\(\\)\\s*[:>]",
        "postgresql.*(error|warning)",
        "unterminated quoted string at or near",
        "operator does not exist:",
        "psqlerror",
        // MSSQL / JET
        "microsoft ole db provider for sql server",
        "microsoft jet database engine error",
        "unclosed quotation mark after the character string '",
        "incorrect syntax near",
        "system\\.data\\.sqlclient\\.sqlexception",
        "[\\d\\-]{6,}.*\\[microsoft\\.ole db provider",
        "incorrect syntax near the keyword",
        // Oracle
        "ora-\\d{5}",
        "oracle error",
        "quoted string not properly terminated",
        "invalid identifier",
        // SQLite
        "sqlite3?::\\w*exception",
        "sqlite_error",
        "unrecognized token:",
        "sqlite.*near \\w+:",
        // generic PHP handlers that expose a DB failure
        "on line \\d+.*(mysql|sql|database)",
        "error.*(fetch|query|select|insert).*(failed|error|exception)",
        "warning.*(supplied|invalid).*argument",
    };

    // ---- WAF / challenge interstitials ----------------------------------

    private static final String[] WAF = {
        "cloudflare",
        "attention required!\\s*\\|\\s*cloudflare",
        "checking your browser before accessing",
        "just a moment",
        "cf-browser-verification",
        "akamai reference #[0-9a-f\\.]+",
        "access denied.*reference #?[0-9a-f\\.]+",
        "incapsula incident id",
        "sucuri cloud",
        "perimeterx",
        "pardon the interruption",
        "request unsuccessful\\. incapsula",
        "web application firewall",
        "\\bray id\\b",
        "captcha|recaptcha",
    };

    // ---- soft 404 / catch-all markers (for admin panel discovery) -------

    private static final String[] SOFT404 = {
        "404", "not found", "page (does not|doesn't) not exist",
        "no longer available", "page you.*looking for",
        "the requested url was not found",
        "under construction", "coming soon", "maintenance",
        "domain (is )?(for sale|parked)", "coming \\^\\^",
    };

    // ---- login-form markers (positive signal for a real admin panel) ---

    private static final String[] LOGIN_FORM = {
        "<input[^>]*type\\s*=\\s*[\"']?password",
        "name\\s*=\\s*[\"']?(passwd|password|user_pass|pwd)[\"']?",
        "<form[^>]*action[^>]*(login|signin|sign_in|logon|auth)",
        "(log ?in|sign ?in|log ?on|authenticate)\\s*(to|as)?\\s*</",
        "forgot (your )?password",
        "remember me",
        "<label[^>]*>\\s*user(name|id)?\\s*</label>",
    };

    private static Pattern compile(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('|');
            sb.append('(').append(arr[i]).append(')');
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static final Pattern DB_ERROR_P  = compile(DB_ERROR);
    private static final Pattern WAF_P      = compile(WAF);
    private static final Pattern SOFT404_P  = compile(SOFT404);
    // NOTE: login-form detection must run on the RAW body, not the normalized one.
    // normalize() strips tags, so every pattern that looks for <input ...> or
    // <form action=...> can never match normalized text.
    private static final Pattern LOGIN_RAW  = compile(LOGIN_FORM);

    /** Strip tags/entities so matching runs on text, not markup. */
    public static String normalize(String body) {
        if (body == null) return "";
        String s = body;
        s = s.replaceAll("(?s)<script.*?</script>", " ");
        s = s.replaceAll("(?s)<style.*?</style>", " ");
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replaceAll("&nbsp;?", " ");
        s = s.replaceAll("&amp;", "&");
        s = s.replaceAll("&lt;", "<");
        s = s.replaceAll("&gt;", ">");
        s = s.replaceAll("&quot;", "\"");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    private static String firstMatch(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    public static String matchDbError(String rawBody) {
        return firstMatch(DB_ERROR_P, normalize(rawBody));
    }

    public static String matchWaf(String rawBody) {
        return firstMatch(WAF_P, normalize(rawBody));
    }

    /** True when the response looks like a catch-all / soft-404 page. */
    public static boolean isSoft404(int status, String rawBody) {
        if (status == 404 || status == 410) return true;
        // a 200 that still screams "not found"
        String t = normalize(rawBody);
        if (hasLoginForm(rawBody)) return false; // a real login form wins
        return SOFT404_P.matcher(t).find();
    }

    /** True when the response positively looks like an actual login page. */
    public static boolean hasLoginForm(String rawBody) {
        return rawBody != null && LOGIN_RAW.matcher(rawBody).find();
    }

    /**
     * The core SQLi decision. Differential by design: the error must appear in
     * the probe response and be ABSENT from the untouched baseline.
     */
    public static Verdict judgeSqli(String baselineBody, String probeBody, int probeStatus) {
        if (isWafStatus(probeStatus) || matchWaf(probeBody) != null) return Verdict.BLOCKED;
        String probeHit = matchDbError(probeBody);
        if (probeHit == null) return Verdict.NOT_VULNERABLE;
        // the decisive check the original apps never did
        if (matchDbError(baselineBody) != null) return Verdict.NOT_VULNERABLE;
        return Verdict.VULNERABLE;
    }

    /**
     * Fingerprint of a response body, used for differential comparison against a
     * site baseline.
     *
     * <p>This exists because "no soft-404 wording" is not proof that a path is
     * real. Plenty of catch-all hosts serve a generic homepage (or an SPA shell)
     * with HTTP 200 for every unknown path and no error text at all. Requiring
     * only the absence of negative evidence is how the original tool produced
     * hundreds of phantom hits.
     */
    public static String fingerprint(String rawBody) {
        String t = normalize(rawBody);
        if (t.isEmpty()) return "empty";
        // hash the normalized text so formatting/attribute-order noise does not
        // make an identical catch-all page look "different"
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(t.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(t.hashCode());
        }
    }

    /** Two bodies are "the same page" if their normalized fingerprints match. */
    public static boolean samePage(String a, String b) {
        return fingerprint(a).equals(fingerprint(b));
    }

    public static boolean isWafStatus(int status) {
        return status == 403 || status == 429 || status == 406 || status == 503;
    }

    public static Verdict judgeAdminPath(int status, String body) {
        // A WAF interstitial served with 200 is NOT a found panel. This was a
        // major false-positive source: every wordlist entry on a Cloudflare-
        // protected host returned 200 with a challenge page.
        if (matchWaf(body) != null) return Verdict.BLOCKED;
        if (status >= 200 && status < 300) {
            if (isSoft404(status, body) && !hasLoginForm(body)) return Verdict.NOT_VULNERABLE;
            return Verdict.VULNERABLE;
        }
        if (status >= 300 && status < 400) return Verdict.BLOCKED; // redirect: report, don't follow
        if (isWafStatus(status)) return Verdict.BLOCKED;
        if (status >= 400) return Verdict.NOT_VULNERABLE;
        return Verdict.ERROR;
    }

    public static List<String> knownEngines() {
        List<String> out = new ArrayList<>();
        out.add("MySQL");
        out.add("PostgreSQL");
        out.add("MSSQL / JET");
        out.add("Oracle");
        out.add("SQLite");
        return out;
    }
}
