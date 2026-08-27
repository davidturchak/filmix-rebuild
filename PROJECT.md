# filmix-rebuild

Two things live here, and it matters which one you are touching.

| | |
|---|---|
| **`client/`** | **filmix-ng** — a new Kotlin/Compose client built against the same Filmix API. This is the product, and where nearly all work happens. Start with the `filmix-client` skill. |
| `apktool_out/`, `jadx_out/` | **`filmixapp-2.2.13.apk`** decompiled — a read-only oracle for how the real client behaves. Editing it is legacy; see the `apk-ui-mod` skill. |

The rebuild exists because the original's structure, not its styling, was the
problem: a `GridView` catalog hard-cast in bytecode, a `DrawerLayout`, v1
`ViewPager` tabs and `android.widget.VideoView` cannot be restyled into a
modern app. On the target tablet it renders as a stretched phone layout using
about a quarter of the width. The original stays installed as the behavioural
reference for API correctness.

## The client

| | |
|---|---|
| Package | `dev.turchak.filmixng` (renamed from `net.filmix.*` — Play Protect blocked the old id) |
| Stack | Kotlin · Compose · Material 3 · Media3 · Coil 3 · Retrofit · Room · Paging 3 |
| minSdk / targetSdk | 26 / 35 |
| Form factors | tablet (primary), phone, Android TV |
| Theme | dark only |
| DI | hand-rolled `AppGraph`, not Hilt |
| Released to | `BUILD/` + `latest.json`, fetched by the in-app updater |

Build, test, device and release workflow — plus the API's sharp edges — are in
the **`filmix-client`** skill (`.claude/skills/filmix-client/`). Read it before
touching `client/`.

## The original app

| | |
|---|---|
| Package | `net.filmix.filmix` |
| Version | 2.2.13 |
| minSdk / targetSdk | 19 / 36 |
| Native libs | none — pure Java/Kotlin |
| Obfuscation | R8, libraries only; the app's own 36 classes keep real names |
| Strings | plaintext, not encrypted |
| Tamper checks | none (no signature check, root check, or attestation in app code) |
| Original signature | v1+v2, `CN=Filmix, OU=Android, O=Filmix, C=RU` |
| Base locale | Russian; also `values-uk`, plus `values-night` |
| Brand accent | `colorAccent` `#fff25100`, `colorPrimaryBg` `#ff292c33` |

## Layout

```
client/          the Compose client — the product              BUILD THIS
BUILD/           published APK + latest.json for the updater
apktool_out/     rebuildable tree — smali + decoded res         legacy edit target
jadx_out/        7,168 .java files — readable reference only    READ ONLY
tools/           apktool.jar 2.11.1, jadx 1.5.1
test.keystore    debug signing key for the *original* (alias "test", pass "android")
filmixapp-2.2.13.apk   pristine original
```

`client/keystore/` holds the release signing key. It is gitignored and
**irreplaceable**: without it no existing install can be updated in place
again. Back it up outside the repo.

`apktool_out/` holds 10,613 smali classes, 194 layouts, 340 drawables,
517 strings, and a 6,406-entry `res/values/public.xml` that pins resource IDs.

## Working on the original

The `apk-ui-mod` skill (`.claude/skills/apk-ui-mod/`) carries the workflow and
three tested helpers:

```bash
S=.claude/skills/apk-ui-mod/scripts

$S/resid.sh 0x7f09006a          # hex -> id/app_name
$S/resid.sh -u app_version      # name -> hex, plus every smali/xml use
$S/find-ui.sh -a AboutActivity  # activity -> layout, ids, theme
$S/find-ui.sh -l activity_main  # layout -> ids, who inflates it
$S/find-ui.sh -t "Настройки"    # visible text -> string res -> users
$S/rebuild.sh                   # build + align + sign + verify
```

The installed `android-reverse-engineering` plugin covers the analysis side
(API extraction, call-flow tracing, Frida).

## Re-decoding from scratch

```bash
java -jar tools/apktool.jar d -f -o apktool_out filmixapp-2.2.13.apk
tools/jadx/bin/jadx -d jadx_out --show-bad-code -j 4 filmixapp-2.2.13.apk
```

## Verified

Full round-trip is proven, not assumed: a smali edit and a resource edit were
rebuilt, signed, and confirmed present in the output APK (v1+v2+v3 verifying).

Two caveats. A rebuild drops the baseline profile in `assets/dexopt/`, costing
some startup AOT optimization — cosmetic. And builds are signed with
`test.keystore`, so a modified APK is a separate app identity: uninstall the
original before installing, and it won't inherit the original's data.
