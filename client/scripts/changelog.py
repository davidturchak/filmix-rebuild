#!/usr/bin/env python3
"""CHANGELOG.md is the single source of release notes.

The author writes bullets under `## Unreleased`; this fills in the version and
build number at release time, because versionCode is the commit count and is
not known until the APK has been built.

    changelog.py check   <file>
    changelog.py release <file> <versionName> <versionCode>

`release` rewrites the Unreleased heading in place and prints the JSON fragment
release.sh splices into latest.json.
"""
import json
import re
import sys

# "## 0.6.7 (83)" or "## Unreleased"
HEADING = re.compile(r"^##\s+(\S+)(?:\s+\((\d+)\))?\s*$")
BULLET = re.compile(r"^-\s+(.*\S)\s*$")

# Old clients are offered every entry the manifest carries, so it cannot grow
# without bound; anything older than this is history nobody is upgrading from.
MAX_ENTRIES = 20


def parse(text):
    """-> [(versionName, versionCode|None, [bullets])] in file order."""
    entries = []
    for line in text.splitlines():
        heading = HEADING.match(line)
        if heading:
            entries.append((heading.group(1), heading.group(2), []))
            continue
        bullet = BULLET.match(line)
        if bullet and entries:
            entries[-1][2].append(bullet.group(1))
    return entries


def unreleased(entries):
    for name, code, bullets in entries:
        if name == "Unreleased":
            return bullets
    return None


def check(path):
    bullets = unreleased(parse(open(path, encoding="utf-8").read()))
    if bullets is None:
        sys.exit(f"error: {path} has no '## Unreleased' section")
    if not bullets:
        sys.exit(f"error: '## Unreleased' in {path} has no entries — "
                 "a release must say what changed")


def release(path, name, code):
    text = open(path, encoding="utf-8").read()
    check(path)

    # Stamp the heading before re-parsing, so the new entry carries its code
    # like every other one and the file needs no second pass at the next release.
    text, count = re.subn(r"^##\s+Unreleased\s*$", f"## {name} ({code})",
                          text, count=1, flags=re.MULTILINE)
    if count != 1:
        sys.exit("error: could not stamp the Unreleased heading")
    open(path, "w", encoding="utf-8").write(text)

    changelog = [
        {"versionCode": int(c), "versionName": n, "notes": b}
        for n, c, b in parse(text) if c and b
    ]
    changelog.sort(key=lambda e: -e["versionCode"])
    changelog = changelog[:MAX_ENTRIES]

    # `notes` stays a plain string carrying the newest release only: clients
    # released before the changelog existed still read it, and changing its
    # type would fail to parse on every one of them.
    newest = changelog[0]["notes"] if changelog else []
    print(json.dumps({"notes": " ".join(newest), "changelog": changelog},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "check":
        check(sys.argv[2])
    elif len(sys.argv) == 5 and sys.argv[1] == "release":
        release(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        sys.exit(__doc__)
