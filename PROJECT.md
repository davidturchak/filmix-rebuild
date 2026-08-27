# filmix-rebuild

Workspace for decoding and modifying **`filmixapp-2.2.13.apk`** (Filmix UHD).

## The app

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
apktool_out/     rebuildable tree — smali + decoded res + manifest   EDIT THIS
jadx_out/        7,168 .java files — readable reference only         READ ONLY
tools/           apktool.jar 2.11.1, jadx 1.5.1
test.keystore    debug signing key (alias "test", pass "android")
filmixapp-2.2.13.apk   pristine original
```

`apktool_out/` holds 10,613 smali classes, 194 layouts, 340 drawables,
517 strings, and a 6,406-entry `res/values/public.xml` that pins resource IDs.

## Working on it

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
