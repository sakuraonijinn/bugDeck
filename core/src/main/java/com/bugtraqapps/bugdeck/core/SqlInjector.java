package com.bugtraqapps.bugdeck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Differential SQLi probe.
 *
 * <p>What the original did, for every link:
 * <pre>
 *   GET(link + "'")           // probe
 *   if body.contains("mysql_fetch") -&gt; "Vulnerable!"
 * </pre>
 * No baseline, so any page that already contained those words scored as a hit.
 *
 * <p>What this does:
 * <pre>
 *   baseline = GET(link)        // may fail -&gt; SKIPPED, not "clean"
 *   probe    = GET(link + "'")
 *   vulnerable only if probe shows a DB error AND baseline does not
 * </pre>
 */
public final class SqlInjector {

    public static final class Result {
        public final String url;
        public final Verdict verdict;
        public final String evidence;   // matched signature, or failure reason
        public final int probeStatus;
        public final int baselineStatus;

        Result(String url, Verdict v, String ev, int ps, int bs) {
            this.url = url; this.verdict = v; this.evidence = ev;
            this.probeStatus = ps; this.baselineStatus = bs;
        }

        public boolean isVulnerable() { return verdict == Verdict.VULNERABLE; }
    }

    public static final String MARKER = "'";

    private final String userAgent;
    private final int timeoutMs;

    public SqlInjector(String userAgent, int timeoutMs) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    public Result probe(String url) {
        Fetcher.Fetch base = Fetcher.get(url, userAgent, timeoutMs);
        if (!base.ok()) {
            return new Result(url, Verdict.SKIPPED, "baseline " + base.failure, 0, 0);
        }
        if (Signature.isWafStatus(base.status)) {
            return new Result(url, Verdict.BLOCKED, "baseline " + base.status, base.status, base.status);
        }

        Fetcher.Fetch probe = Fetcher.get(url + MARKER, userAgent, timeoutMs);
        if (!probe.ok()) {
            return new Result(url, Verdict.SKIPPED, "probe " + probe.failure, 0, base.status);
        }

        Verdict v = Signature.judgeSqli(base.body, probe.body, probe.status);
        String ev;
        if (v == Verdict.VULNERABLE) {
            ev = String.valueOf(Signature.matchDbError(probe.body));
        } else if (v == Verdict.BLOCKED) {
            String w = Signature.matchWaf(probe.body);
            ev = w != null ? w : ("http " + probe.status);
        } else {
            ev = "";
        }
        return new Result(url, v, ev, probe.status, base.status);
    }

    public List<Result> probeAll(List<String> urls) {
        List<Result> out = new ArrayList<>();
        for (String u : urls) out.add(probe(u));
        return out;
    }
}
