#!/usr/bin/env bash
# Typecheck the Android GUI sources without an Android SDK.
#
# The core/CLI suites in build.sh run against the real JVM. This script exists
# for the app module: it compiles the GUI against generated stubs, which is
# enough to catch a renamed core method, a wrong argument type, a missing
# string resource, or a reference to a view that was deleted from a layout.
# It does NOT replace aapt/gradle, so it cannot catch a real resource error.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
STUB="$HERE/stub"
OUT="$HERE/out"

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
command -v python3 > /dev/null || { echo "python3 not found"; exit 1; }

echo "==> generating stubs"
rm -rf "$STUB" "$OUT"
mkdir -p "$OUT"
python3 "$HERE/make_stubs.py" > /dev/null

echo "==> compiling stubs"
javac -nowarn -d "$OUT" $(find "$STUB" -name '*.java')

echo "==> compiling app sources + core against stubs"
CORE=$(find "$ROOT/core/src/main/java" -name '*.java')
if [ ! -d "$ROOT/app/src/main/java" ]; then
  echo "no app sources: $ROOT/app/src/main/java does not exist"
  echo "GUI TYPECHECK FAILED"
  exit 1
fi
APP=$(find "$ROOT/app/src/main/java" -name '*.java')
if [ -z "$APP" ]; then
  # Report this as a failure rather than a green "0 sources typecheck".
  echo "no app sources found under $ROOT/app/src/main/java"
  echo "GUI TYPECHECK FAILED"
  exit 1
fi
if ! javac -nowarn -cp "$OUT" -d "$OUT" \
      "$HERE/R.java" $CORE $APP 2> "$HERE/.err"; then
  cat "$HERE/.err"
  echo "GUI TYPECHECK FAILED"
  exit 1
fi
cat "$HERE/.err" 2>/dev/null || true
rm -f "$HERE/.err"

echo "==> ok: $(echo $APP | wc -w) app source(s) typecheck"
