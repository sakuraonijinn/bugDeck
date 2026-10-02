package com.bugtraqapps.bugdeck.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin panel / login path discovery.
 *
 * <p>Two defects in the original are fixed here:
 *
 * <ol>
 *   <li><b>Soft 404s.</b> The original called a path "Found!" purely on
 *       {@code statusCode == 200}. Any host with a catch-all 200 (SPA, parked
 *       page, "under construction") reported every one of its wordlist entries as
 *       a hit — hundreds of false positives per scan.</li>
 *   <li><b>Sticky status.</b> {@code Status} was a fragment field reused across
 *       the whole loop and never reset when a request threw. One {@code "Found!"}
 *       made every subsequent failed request inherit it.</li>
 * </ol>
 */
public final class AdminFinder {

    public static final class Hit {
        public final String path;
        public final String url;
        public final int status;
        public final Verdict verdict;
        public final String note;
        final String body;   // retained for catch-all baseline comparison

        Hit(String path, String url, int status, Verdict v, String note) {
            this(path, url, status, v, note, "");
        }

        Hit(String path, String url, int status, Verdict v, String note, String body) {
            this.path = path; this.url = url; this.status = status;
            this.verdict = v; this.note = note; this.body = body;
        }
    }

    private static final String[] EXT_SETS = {
        "php", "php5", "html", "htm", "asp", "aspx", "jsp", "cgi", "pl", "cfm"
    };

    private final String userAgent;
    private final int timeoutMs;
    private final int threads;
    private final int perHostCap;
    private boolean followRedirects = false;

    public AdminFinder(String userAgent, int timeoutMs, int threads) {
        this(userAgent, timeoutMs, threads, 0);
    }

    /**
     * @param perHostCap maximum requests in flight against a single host; 0 means
     *                   unlimited. Enforced by a semaphore in {@link #check} so a
     *                   scan cannot hammer one origin regardless of thread count.
     */
    public AdminFinder(String userAgent, int timeoutMs, int threads, int perHostCap) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
        this.threads = Math.max(1, threads);
        this.perHostCap = perHostCap;
    }

    /** Lazily created; all scans target one host so one gate is enough. */
    private volatile java.util.concurrent.Semaphore hostGate;

    private java.util.concurrent.Semaphore gate() {
        java.util.concurrent.Semaphore g = hostGate;
        if (g == null) {
            synchronized (this) {
                g = hostGate;
                if (g == null) {
                    g = perHostCap > 0
                        ? new java.util.concurrent.Semaphore(perHostCap)
                        : null;
                    hostGate = g;
                }
            }
        }
        return g;
    }

    /**
     * Whether 3xx responses are followed. Off by default: the original followed
     * them silently, so {@code /admin -> /login} was reported as an admin panel
     * at the wrong path.
     */
    public void setFollowRedirects(boolean follow) { this.followRedirects = follow; }

    public List<Hit> scan(String baseUrl, List<String> wordlist) throws InterruptedException {
        return scan(baseUrl, wordlist, null);
    }

    public List<Hit> scan(String baseUrl, List<String> wordlist, ProgressListener listener)
            throws InterruptedException {
        return scan(baseUrl, wordlist, listener, 3);
    }

    /**
     * Expand the {@code %EXT%} token the original shipped but never implemented.
     * Every {@code administrator/login.%EXT%} entry in the recovered wordlists was
     * being requested literally, so all 96 of them were guaranteed misses.
     */
    public static List<String> expand(String entry) {
        if (entry == null) return new ArrayList<>();
        if (!entry.contains("%EXT%")) {
            List<String> one = new ArrayList<>();
            one.add(entry);
            return one;
        }
        List<String> out = new ArrayList<>();
        for (String ext : EXT_SETS) {
            out.add(entry.replace("%EXT%", ext));
        }
        return out;
    }

    /** Join base + path correctly; the original did naive string concat. */
    public static String join(String base, String path) {
        String p = path.trim();
        // an absolute entry (a pre-seeded target) must not be glued onto the base
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (p.isEmpty()) return b;
        if (p.startsWith("/")) p = p.substring(1);
        return b + "/" + p;
    }

    /**
     * Find admin/login paths, using a random-path baseline to defeat catch-all
     * hosts.
     *
     * <p>The baseline matters: a server that returns the same generic page with
     * HTTP 200 for every unknown path gives no soft-404 wording, so a
     * marker-only check reports every wordlist entry as a hit. Comparing each
     * response against a URL that is guaranteed not to exist catches that.
     */
    public List<Hit> scan(String baseUrl, List<String> wordlist, ProgressListener listener,
                          int baselineSamples) throws InterruptedException {

        List<String> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String w : wordlist) {
            for (String expanded : expand(w)) {
                String full = join(baseUrl, expanded);
                if (seen.add(full)) targets.add(full);
            }
        }

        // ---- baseline: probe random paths that cannot exist -------------
        // Interruption is checked between probes. Without this, cancelling during
        // the baseline phase did nothing until all 3 requests finished, which is
        // what made STOP feel broken.
        StringBuilder baselineFps = new StringBuilder();
        int baselineOk = 0;
        for (int i = 0; i < Math.max(1, baselineSamples); i++) {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<>();
            String rnd = join(baseUrl, "zq" + Long.toHexString(
                (long) (Math.random() * 0xFFFFFFFL)) + "x");
            Fetcher.Fetch f = Fetcher.get(rnd, userAgent, timeoutMs);
            if (f.ok() && f.status == 200) {
                baselineFps.append(Signature.fingerprint(f.body)).append('|');
                baselineOk++;
            }
        }
        final String baseline = baselineFps.toString();
        final boolean hasBaseline = baselineOk > 0;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Hit>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        final int total = targets.size();

        for (final String url : targets) {
            futures.add(pool.submit(() -> {
                Hit h = check(url);
                // A response identical to the site's own catch-all page is not a
                // discovery, no matter what status it carried.
                if (hasBaseline && h.verdict == Verdict.VULNERABLE
                        && baseline.contains(Signature.fingerprint(h.body))) {
                    h = new Hit(h.path, h.url, h.status, Verdict.NOT_VULNERABLE,
                            "matches site catch-all baseline", h.body);
                }
                if (listener != null) {
                    int d = done.incrementAndGet();
                    listener.onProgress(d, total, h);
                }
                return h;
            }));
        }

        List<Hit> hits = new ArrayList<>();
        try {
            for (Future<Hit> f : futures) {
                // If this thread was interrupted (cancelled), stop waiting on the
                // remaining futures. Without this the scan still had to drain the
                // whole queue one f.get() at a time, so STOP appeared to do nothing
                // for as long as the wordlist was long.
                if (Thread.currentThread().isInterrupted()) break;
                try {
                    hits.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    // a worker died; record it rather than losing the whole scan
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    hits.add(new Hit("", "", 0, Verdict.ERROR,
                        "worker failed: " + cause));
                }
            }
        } finally {
            // shutdownNow, not shutdown: shutdown() lets queued tasks finish, which
            // is exactly the "hangs while stopping" behaviour. Then cancel anything
            // still pending so the queue is actually dropped.
            pool.shutdownNow();
            for (Future<Hit> f : futures) f.cancel(true);
            // Do not block on termination. A worker parked in a socket read can
            // sit here for the full timeout; the caller has already moved on.
            pool.awaitTermination(250, TimeUnit.MILLISECONDS);
        }
        return hits;
    }

    /** One request. Status is local, so it can never leak between iterations. */
    public Hit check(String url) {
        java.util.concurrent.Semaphore g = gate();
        if (g != null) {
            try {
                g.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Hit(url, url, 0, Verdict.SKIPPED, "interrupted");
            }
        }
        try {
            return checkUnderGate(url);
        } finally {
            if (g != null) g.release();
        }
    }

    private Hit checkUnderGate(String url) {
        Fetcher.Opts o = new Fetcher.Opts().ua(userAgent).timeout(timeoutMs);
        if (followRedirects) o.follow(Fetcher.DEFAULT_MAX_REDIRECTS);
        Fetcher.Fetch f = Fetcher.get(url, o);
        if (!f.ok()) {
            // the reason is preserved instead of being swallowed
            return new Hit(url, url, 0, Verdict.SKIPPED, f.failure);
        }
        Verdict v = Signature.judgeAdminPath(f.status, f.body);
        String note = "";
        if (v == Verdict.VULNERABLE) {
            note = Signature.hasLoginForm(f.body) ? "login form" : "200, no soft-404 markers";
        } else if (v == Verdict.BLOCKED && f.redirected) {
            note = "redirects to " + f.finalUrl;
        }
        return new Hit(url, url, f.status, v, note, f.body);
    }

    public interface ProgressListener {
        void onProgress(int done, int total, Hit hit);
    }
}
