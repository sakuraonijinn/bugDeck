#!/usr/bin/env bash
# Build and test BugDeck CLI. No Android SDK required.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT="$HERE"
OUT="$HERE/bin"
rm -rf "$OUT"; mkdir -p "$OUT"

# The harness JDK on-device, else whatever is already on PATH (CI, desktop).
# Hardcoding a single path broke this on both a GitHub runner (no such path) and
# on the device itself after the toolchain moved, so try the known locations and
# fall back to PATH.
if [ -z "$JAVA_HOME" ]; then
  for candidate in \
      "/data/local/tmp/androidharness/com.androidharness.app/linux/lib/jvm/java-17-openjdk" \
      "/data/user/0/com.androidharness.app/files/linux/lib/jvm/java-17-openjdk"
  do
    if [ -x "$candidate/bin/javac" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -n "$JAVA_HOME" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v javac > /dev/null || { echo "javac not found; set JAVA_HOME"; exit 1; }

CORE=$(find "$HERE/core/src/main/java" -name '*.java')

echo "==> compiling core + cli"
if ! javac -d "$OUT" $CORE "$ROOT/notes/cli/BugDeck.java" 2> "$OUT/.err"; then
  grep -v "Unexpected extension" "$OUT/.err" | grep -v "^[0-9]* warning" || true
  echo "BUILD FAILED (core + cli)"
  exit 1
fi
grep -v "Unexpected extension" "$OUT/.err" | grep -v "^[0-9]* warning" || true

if [ ! -f "$OUT/BugDeck.class" ]; then
  echo "BUILD FAILED: BugDeck.class missing"
  exit 1
fi

# compile tests + fixture; a failure here must abort, not print "ok"
echo "==> compiling tests + fixture"
if ! javac -cp "$OUT" -d "$OUT" \
  "$ROOT/notes/CoreTest.java" \
  "$ROOT/notes/ApiTest.java" \
  "$ROOT/notes/cli/WireTest.java" \
  "$ROOT/notes/cli/DorkTest.java" \
  "$ROOT/notes/cli/ProviderTest.java" \
  "$ROOT/notes/cli/FixtureSite.java" 2> "$OUT/.err"; then
  grep -v "Unexpected extension" "$OUT/.err" | grep -v "^[0-9]* warning" \
    | grep -v "unchecked\|Recompile" || true
  echo "BUILD FAILED (tests)"
  exit 1
fi
grep -v "Unexpected extension" "$OUT/.err" | grep -v "^[0-9]* warning" \
  | grep -v "unchecked\|Recompile" || true

echo "==> running all suites"
FAILED=0
for t in CoreTest WireTest DorkTest ProviderTest; do
  printf "  %-13s " "$t"
  # Each suite opens real sockets; a leaked non-daemon thread would hang the
  # build forever instead of failing it, so give each one a hard ceiling.
  if timeout 180 java -cp "$OUT" "$t" 2>&1 | tail -1 | tee "$OUT/.last"; then :; fi
  grep -q "FAIL=0" "$OUT/.last" || FAILED=1
done
[ "$FAILED" -eq 0 ] || { echo "TESTS FAILED"; exit 1; }

# the core ships in the Android app too, so keep it off desktop-only APIs
echo "==> checking core stays Android-safe"
rm -rf "$OUT/.coreonly"; mkdir -p "$OUT/.coreonly"
cp -r "$OUT/com" "$OUT/.coreonly/"
printf "  %-13s " "ApiTest"
java -cp "$OUT" ApiTest "$OUT/.coreonly" 2>&1 | tail -1 | tee "$OUT/.last"
grep -q "FAIL=0" "$OUT/.last" || { echo "API CHECK FAILED"; exit 1; }
rm -rf "$OUT/.coreonly" "$OUT/.err" "$OUT/.last"

# The GUI cannot be run without an SDK, but it can still be typechecked -- only
# when the app sources are actually present. Without this guard the step runs
# with an empty source list and reports "0 app source(s) typecheck", i.e. green
# while checking nothing.
if [ -d "$HERE/app/src/main/java" ]; then
  echo "==> checking Android resources"
  if ! python3 "$HERE/verify/check_res.py" 2>&1 | sed 's/^/  /'; then
    echo "RESOURCE CHECK FAILED"
    exit 1
  fi

  echo "==> typechecking the Android GUI"
  if ! sh "$HERE/verify/check.sh" 2>&1 | sed 's/^/  /'; then
    echo "GUI TYPECHECK FAILED"
    exit 1
  fi
fi

echo "==> ok"
echo
echo "run:  java -cp $OUT BugDeck help"
echo "test: java -cp $OUT CoreTest && java -cp $OUT WireTest && java -cp $OUT DorkTest"
