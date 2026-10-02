# BugDeck CLI

The detection core recovered from three Android apps (Droidbug SQLi Spyder,
Droidbug Admin Panel Finder, DH HackBar), rewritten to stop producing false
positives, and runnable from a terminal.

No Android SDK needed.

## Build and test

```sh
sh bugdeck/build.sh
```

That compiles everything and runs 292 checks across 4 suites. Any failure aborts
with a non-zero exit.

## Commands

```sh
BugDeck sqli <url>... [-i list.txt] [--crawl] [-t SEC] [-o out.json]
BugDeck admin <baseUrl> -w <list> [-n N] [--threads N] [--follow-redirects]
BugDeck crawl <seedUrl> [-d depth] [--max-pages N] [--cross-host]
BugDeck dork <expr> [-d domain] -p brave|google -k KEY [--pages N] [-cx ID]
BugDeck wordlists
BugDeck payloads [--list] [category]
```

Common flags: `-t SEC` (timeout, default 8), `-ua STRING`, `-o FILE` (JSON out).

### Examples

```sh
# probe specific URLs
BugDeck sqli "http://site/page.php?id=1"

# crawl a site then probe everything it finds
BugDeck sqli http://site --crawl -d 2 --max-pages 50

# find admin panels
BugDeck admin http://site -w mix1 -n 60 --threads 12

# list what a crawl would discover, without probing
BugDeck crawl http://site -d 2

# search via Brave (key via flag or BRAVE_API_KEY)
BugDeck dork 'inurl:admin' -p brave --pages 3

# Google Programmable Search (needs both key and search-engine id)
BugDeck dork 'intext:"sql syntax error"' -p google -k KEY -cx CXR_ID
```

## Verdicts

| Verdict | Meaning |
|---|---|
| `VULNERABLE` | DB error signature in the probe that the clean URL did not have |
| `NOT_VULNERABLE` | probe showed nothing the baseline did not also show |
| `BLOCKED` | 403/429/WAF challenge — inconclusive, **not** a clean result |
| `SKIPPED` | timeout / DNS / TLS — never reported as clean |

The last two exist because the original apps reported both as "Not Vulnerable",
which is the single most misleading thing they did.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | ran, nothing vulnerable |
| 1 | at least one `VULNERABLE` |
| 2 | bad usage or missing configuration |
| 3 | inconclusive (blocked or skipped, no verdict) |

Usable in CI: `BugDeck sqli ... \|\| echo "found something"`.

## What was fixed, and why

The three original apps shared the same failure modes. Each fix below maps to a
verified test.

**SQLi had no baseline.** The original fetched `url + "'"` and grepped the body
for `mysql_fetch_array` / `Query failed` with no clean fetch for comparison. Any
page that legitimately mentions those words scored "Vulnerable!" — a blog post
about MySQL, phpMyAdmin, a WordPress site. BugDeck always fetches the clean URL
first and only reports a hit when the error appears *only* in the probe.

**Admin finder trusted any 200.** The original called a path found purely on
`statusCode == 200`, so any host with a catch-all response reported every
wordlist entry. BugDeck fingerprints random nonexistent paths to learn the
site's catch-all page, then discards any response matching it, and additionally
requires either a login-form marker or the absence of soft-404 wording.

**Status leaked between requests.** The original reused a fragment field for
the verdict and never reset it on exception, so one "Found!" made every
subsequent failure inherit it. Status is now a local variable per request.

**Empty catch blocks.** All three apps swallowed `IOException`, making an
unreachable host indistinguishable from a clean one. Every failure now carries a
reason and lands in `SKIPPED`.

**`==` on strings.** The original compared verdict strings by reference
identity, so results rendered nondeterministically. Everything uses `.equals()`.

**`%EXT%` was never expanded.** The recovered `mix1` wordlist has 96 entries
like `administrator/login.%EXT%` that were requested literally — guaranteed
misses. BugDeck expands them across 10 extensions.

**No timeouts.** Two of the three apps set none, so one slow host hung the whole
scan. Every request has connect and read timeouts.

**"SQLi Scanner" that wasn't.** DH HackBar's Web Tools entry opened a WebView to
`pentest-tools.com`, sending the target URL to a third party. Those are not in
BugDeck; the scanner is local.

## Layout

```
core/src/main/java/com/bugtraqapps/bugdeck/
  core/    Fetcher, Signature, SqlInjector, AdminFinder, Crawler, HtmlLinks, Verdict
  dork/    DorkProvider, DorkProviders, DorkSearch, DorkQuery, JsonLite
app/src/main/java/com/bugtraqapps/bugdeck/
  MainActivity, TabsAdapter          ViewPager2 shell
  ScannerFragment                    SQLi probe + admin finder + crawl
  HackBarFragment                    payload injection into a URL parameter
  DorkFragment                       search across 7 providers
  SettingsDialog, Payloads           credentials, payload index
app/src/main/res/                    Material 3 layouts, themes, crimson palette
assets/
  wordlists/  8 lists, deduped, %EXT% preserved
  payloads.json  309 payloads, 14 categories
notes/           BugDeck.java (the CLI), the test suites, html fixtures
verify/          Android stubs + GUI typecheck, and the icon preview
```

`core` is shared with the Android app and is deliberately plain `java.io` +
`java.net` + `java.util.regex`; `ApiTest` fails the build if a desktop-only API
creeps in.

## Android app

`app/` is an ordinary Android module and needs the SDK to package. The
detection core does not, which is why `bash build.sh` can run on a bare JDK.

```sh
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
```

`.github/workflows/build.yml` does both: `core` runs `build.sh` on a plain JDK,
then `apk` builds and uploads the APK. **The repo root is this directory** — the
scripts resolve their own paths and do not expect a parent project.

`verify/check.sh` typechecks the GUI against generated Android stubs, so a
renamed core method or a missing string resource fails without an SDK. It does
**not** replace aapt/gradle: it cannot catch a malformed layout or vector, so
the first real device build is still the honest test.

## Tests

| Suite | Checks | Covers |
|---|---|---|
| `CoreTest` | 30 | signature matching, differential SQLi, soft-404, `%EXT%`, sticky-status regression |
| `WireTest` | 40 | auth headers, redirect chains and loops, dork pagination, crawler rules |
| `DorkTest` | 30 | query building, both API response shapes, malformed JSON |
| `ApiTest` | 192 | core stays Android-safe |

`FixtureSite` serves a site that reproduces the exact false positives from the
originals. Start it with `java -cp bugdeck/bin FixtureSite` (port 8731).

## Known limits

- Only GET, no POST body fuzzing or header injection testing.
- SQLi detection is error-based differential; boolean- and time-based blind
  techniques are in the payload set but not automated.
- No TLS certificate validation override (good).
- Admin finder has no authentication — it detects login panels, not creds.
- Crawler is single-threaded and same-host by default; `--cross-host` exists but
  you should have permission before using it.
