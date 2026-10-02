package com.bugtraqapps.bugdeck.core;

/**
 * Outcome of a single probe. The important change vs. the original apps is that
 * "not vulnerable" and "we could not tell" are distinct values, so a timeout or
 * a WAF block is never silently reported as a clean result.
 */
public enum Verdict {
    VULNERABLE,
    NOT_VULNERABLE,
    BLOCKED,      // 403/429 or WAF interstitial - inconclusive, not a clean bill
    SKIPPED,      // timeout, DNS failure, TLS error
    ERROR         // anything else
}
