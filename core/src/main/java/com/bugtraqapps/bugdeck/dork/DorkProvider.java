package com.bugtraqapps.bugdeck.dork;

import com.bugtraqapps.bugdeck.core.HtmlLinks;

import java.util.List;

/**
 * A dork/search backend.
 *
 * <p>Implementations are optional and independent: the Dork tab is unusable until
 * one is configured, and every implementation below ships with its route already
 * built, so enabling one is a matter of supplying a credential (or nothing at all
 * for the keyless engines).
 */
public interface DorkProvider {

    /** Stable id used in config files and the CLI ({@code -p brave}). */
    String id();

    /** Human label for menus. */
    String title();

    /**
     * Whether this provider needs a credential before it will work.
     * Keyless providers return false.
     */
    boolean requiresKey();

    /** What the user must obtain, and where. Shown in Settings. */
    String keyHint();

    /**
     * Build the search URL for a dork.
     *
     * @param dork    the raw operator expression, e.g. {@code site:example.com "admin"}
     * @param domain  optional site/domain restriction; may be empty
     * @param page    1-based result page
     * @param key     credential, may be empty when {@link #requiresKey()} is false
     * @return a fully-formed URL, or null if the inputs cannot form a valid query
     */
    String buildUrl(String dork, String domain, int page, String key);

    /**
     * Pull result URLs out of a search response body.
     *
     * <p>Defaults to a generic href harvest; providers with structured output
     * (JSON APIs) override this.
     */
    default List<String> parse(String body, String contentType) {
        return HtmlLinks.extract(body);
    }

    /**
     * How many result pages this provider will serve. Some keyless HTML engines
     * stop paginating early; the UI uses this to cap the spinner.
     */
    default int maxPages() {
        return 10;
    }
}
