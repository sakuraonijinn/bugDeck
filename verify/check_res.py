#!/usr/bin/env python3
"""Static checks on app/src/main/res that catch the errors javac cannot.

The GUI typecheck compiles Java against generated stubs, so it proves method
names and types -- but it never parses a layout or a vector. Those failures
only surface when aapt runs, i.e. on CI after a full checkout and SDK setup.
This script moves them forward to a fast local step.

Checks:
  1. every XML file parses (catches duplicate attributes, unclosed tags)
  2. no "--" inside an XML comment, which is illegal per the XML spec and
     which is what broke the very first CI build
  3. every R.id.<name> referenced from Java is declared as @+id/<name> in res/
  4. every @string/<name> referenced from a layout exists in strings.xml
  5. every @style/<name> referenced from a layout exists in themes.xml
  6. every @color/<name> referenced from a layout exists in colors.xml
"""
import glob
import os
import re
import sys
import xml.dom.minidom as minidom

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")
JAVA = os.path.join(ROOT, "app", "src", "main", "java")

failures = []


def xml_files():
    return sorted(glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True))


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# ---- 1 + 2: parse and comment hygiene -------------------------------------
for path in xml_files():
    rel = os.path.relpath(path, ROOT)
    try:
        minidom.parse(path)
    except Exception as e:
        failures.append("XML does not parse: %s: %s" % (rel, e))
        continue
    src = read(path)
    for m in re.finditer(r"<!--(.*?)-->", src, re.S):
        if "--" in m.group(1):
            line = src[: m.start()].count("\n") + 1
            failures.append(
                "illegal '--' inside an XML comment: %s:%d" % (rel, line))


# ---- declared resources ---------------------------------------------------
def declared(kind, folder):
    """Collect resource names declared with that tag. Style names may contain
    dots (Widget.BugDeck.Tabs), so keep the whole dotted name."""
    names = set()
    for path in glob.glob(os.path.join(RES, folder, "*.xml")):
        for m in re.finditer(r'<(?:color|string|style)\s+name="([\w.]+)"', read(path)):
            names.add(m.group(1))
    return names


strings = declared("string", "values")
colors = declared("color", "values")
colors_night = declared("color", "values-night")
styles = declared("style", "values") | declared("style", "values-night")

ids = set()
for path in xml_files():
    for m in re.finditer(r"@\+id/(\w+)", read(path)):
        ids.add(m.group(1))


# ---- 3: R.id references ---------------------------------------------------
for path in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True):
    rel = os.path.relpath(path, ROOT)
    for m in re.finditer(r"R\.id\.(\w+)", read(path)):
        if m.group(1) not in ids:
            failures.append("java references undeclared R.id.%s: %s" % (m.group(1), rel))


# ---- 4/5/6: @string @style @color referenced from res ----------------------
for path in xml_files():
    rel = os.path.relpath(path, ROOT)
    src = read(path)
    for m in re.finditer(r"@string/([\w.]+)", src):
        if m.group(1) not in strings:
            failures.append("layout references unknown @string/%s: %s" % (m.group(1), rel))
    # Style names contain dots; the regex must capture the whole dotted name or
    # "Widget.BugDeck.Tabs" is truncated to "Widget" and looks undeclared.
    # Styles under Widget.Material3.* come from the Material library, not from
    # this repo, so they cannot be verified without the dependency on disk.
    for m in re.finditer(r"(?<![?@/])@style/([\w.]+)", src):
        name = m.group(1)
        if name.startswith("Widget.Material3.") or name.startswith("Theme.Material3."):
            continue
        if name not in styles:
            failures.append("layout references unknown @style/%s: %s" % (name, rel))
    for m in re.finditer(r"(?<![?@/])@color/([\w.]+)", src):
        name = m.group(1)
        if name not in colors and name not in colors_night:
            failures.append("layout references unknown @color/%s: %s" % (name, rel))


# ---- report ---------------------------------------------------------------
print("checked %d xml file(s) under app/src/main/res" % len(xml_files()))
if failures:
    print()
    for f in sorted(set(failures)):
        print("  FAIL " + f)
    print()
    print("RESOURCE CHECK FAILED (%d problem(s))" % len(set(failures)))
    sys.exit(1)

print("resources OK: xml parses, comments legal, all ids/strings/styles/colors resolve")
