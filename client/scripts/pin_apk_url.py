#!/usr/bin/env python3
"""Point BUILD/latest.json's apkUrl at the commit that contains the APK.

release.sh cannot do this itself: it refuses to run on a dirty tree, so the
commit carrying BUILD/ does not exist yet when the manifest is written, and the
`commit` it records is the *source* commit, whose tree has no APK in it.

Run this after the publish commit. See "Pin apkUrl to the publish commit" in
.claude/skills/filmix-client/SKILL.md for why a branch ref will not do.
"""
import json
import subprocess
import sys

MANIFEST = "BUILD/latest.json"
REPO = "https://raw.githubusercontent.com/davidturchak/filmix-rebuild"


def main() -> int:
    # Explicitly UTF-8, never the locale default: the notes and changelog are
    # Russian, and under LC_ALL=C — a cron job, a bare CI container — the
    # default is ASCII and this read dies, which silently leaves the release
    # pointing at the branch ref that intermittently 400s.
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    name = manifest["apkUrl"].rsplit("/", 1)[1]

    # The commit that last touched this APK, not HEAD: pinning survives the
    # doc-only commits that tend to follow a release.
    sha = subprocess.run(
        ["git", "log", "-1", "--format=%H", "--", f"BUILD/{name}"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    if not sha:
        print(f"error: BUILD/{name} is not committed yet", file=sys.stderr)
        return 1

    # Prove the blob is really in that tree before naming it in a manifest that
    # every installed client reads.
    listed = subprocess.run(
        ["git", "ls-tree", "--name-only", sha, f"BUILD/{name}"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    if listed != f"BUILD/{name}":
        print(f"error: {sha[:7]} does not contain BUILD/{name}", file=sys.stderr)
        return 1

    before = manifest["apkUrl"]
    manifest["apkUrl"] = f"{REPO}/{sha}/BUILD/{name}"
    if manifest["apkUrl"] == before:
        print("already pinned")
        return 0

    # notes stays a string: every client released before the changelog existed
    # parses this key and an array throws on install in the field.
    assert isinstance(manifest["notes"], str), "notes must stay a string"

    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"was: {before}\nnow: {manifest['apkUrl']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
