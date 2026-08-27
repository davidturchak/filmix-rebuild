---
name: apk-ui-mod
description: Modify the UI of a compiled Android APK — layouts, themes, colors, strings, drawables, dimensions — then rebuild, sign, and install it. Covers mapping an on-screen element back to its layout/resource, resolving the hex resource IDs that smali uses, editing smali for UI behavior R8 baked into code, and the apktool→zipalign→apksigner loop. Use when changing how a decompiled APK looks or behaves on screen, restyling/theming/reskinning an app, translating or renaming UI text, swapping icons, or repacking a patched APK.
---

# APK UI Modification

Change how a compiled app looks, then get a working APK back out. This is
resource-and-smali surgery on a decoded APK — there is no source tree and no
Gradle. You edit what apktool produced and apktool puts it back together.

## Ground rules

**Edit `apktool_out/` only.** That is the single rebuildable tree.
`jadx_out/` is a *read-only* reference — its Java is for reading, never
recompilable. Use jadx to understand, edit smali and res.

**Never hand-edit `res/values/public.xml`.** It pins every resource to its
numeric ID. Smali refers to resources only by those hex constants, so a shifted
ID silently rewires the whole app to the wrong views. apktool honors this file
on rebuild, which is exactly what makes editing safe. Adding a *new* resource is
fine — it gets a fresh ID appended. Deleting or reordering entries is not.

**Every rebuild must be re-signed.** An unsigned or misaligned APK will not
install. Use `scripts/rebuild.sh`, which does the whole chain and verifies.

## Orientation

Check `PROJECT.md` in the repo root for the concrete paths, package name, and
toolchain locations for the APK in this workspace. In general:

| Path | What it holds |
|---|---|
| `apktool_out/res/layout/` | Screen and item layouts (XML) |
| `apktool_out/res/values/` | `colors` `strings` `styles` `dimens` `arrays` |
| `apktool_out/res/values-night/` | Dark-mode style overrides |
| `apktool_out/res/values-<lang>/` | Per-locale strings |
| `apktool_out/res/drawable*/` | Shapes, vectors, PNGs (`-anydpi-v21` = vectors) |
| `apktool_out/res/mipmap-*/` | Launcher icons |
| `apktool_out/smali*/` | Dalvik bytecode — UI behavior lives here |
| `apktool_out/AndroidManifest.xml` | Activities, themes, label, permissions |

Library layouts (`abc_`, `mtrl_`, `m3_`, `design_`, `lb_`) come from AndroidX /
Material and are usually *not* what you want. App-owned layouts are the rest.

## Finding the thing you want to change

Three routes in, depending on what you can see. `scripts/find-ui.sh` automates
all three.

**From visible text** — grep the string, get its name, find who uses it:

```bash
grep -n 'Some visible text' apktool_out/res/values*/strings.xml
grep -rn '@string/that_name' apktool_out/res/layout/
```

Remember the localized copies. Changing only `values/strings.xml` leaves every
`values-<lang>/` override in place, so the change won't show for those users.

**From a screen** — activity → layout. Find the `setContentView` ID in smali,
then resolve it:

```bash
grep -n 'setContentView' apktool_out/smali*/<path>/SomeActivity.smali
./.claude/skills/apk-ui-mod/scripts/resid.sh 0x7f0c001d   # -> layout/activity_x
```

**From a hex constant in smali** — this is the common case. Code addresses views
as bare numbers:

```smali
const v3, 0x7f09006a
invoke-virtual {p0, v3}, ...->findViewById(I)Landroid/view/View;
```

`resid.sh` resolves both directions — hex to name, and name to hex:

```bash
./.claude/skills/apk-ui-mod/scripts/resid.sh 0x7f09006a   # -> id/app_name
./.claude/skills/apk-ui-mod/scripts/resid.sh app_name     # -> 0x7f09006a
```

Name-to-hex is what you need to grep smali for every use of a widget.

## Making the change

**Restyling** — themes cascade, so prefer them over editing layouts one by one.
Change the palette in `res/values/colors.xml` and the theme in
`res/values/styles.xml`; check `values-night/styles.xml` for the dark variant
so the two don't drift apart.

**Layout** — edit the XML in `res/layout/` directly. Keep any
`android:id` you don't intend to break: removing an id that smali resolves via
`findViewById` turns it into a null and usually crashes the screen. Adding new
views is safe; give them new ids and apktool allocates fresh resource IDs.

**Text** — `res/values/strings.xml` plus the localized siblings.

**Icons** — replace files in `res/mipmap-*/` and `res/drawable-*/`, matching the
original filename *and* pixel dimensions per density bucket. `.9.png` nine-patch
files carry their stretch/padding data in a 1px transparent border — re-export
as a real nine-patch or the frame gets treated as image content.

**UI behavior in code** — visibility toggles, adapters, conditionals, click
handlers, and dynamically built views are in smali, not XML. Locate them by
resolving the resource id to hex and grepping. Keep smali edits minimal and
register-safe: changing a string literal or a constant is low-risk; restructuring
control flow means honoring `.locals` and avoiding clobbered registers.

## Rebuild, sign, install

```bash
./.claude/skills/apk-ui-mod/scripts/rebuild.sh              # -> build/<name>-signed.apk
./.claude/skills/apk-ui-mod/scripts/rebuild.sh -o mymod.apk
```

It runs `apktool b` → `zipalign -p 4` → `apksigner sign` (v1+v2+v3) →
`apksigner verify`, and fails loudly at whichever step breaks. Then:

```bash
adb install -r build/<name>-signed.apk
```

The rebuilt APK is signed with *your* key, not the original developer's. Android
refuses to update an installed app across a signature change, so **uninstall the
original first** — and note the modified build is a distinct identity to the OS:
it will not see the original's saved data.

## Pitfalls

- **`aapt2` fails on rebuild** — almost always malformed XML you just edited, or
  a reference to a resource that doesn't exist. The error names the file and
  line; read it rather than guessing.
- **Builds but crashes on launch** — usually a removed/renamed `android:id` that
  smali still resolves, or a layout whose root type changed under a cast in code.
  `adb logcat` names the class and line.
- **Change doesn't appear** — a more specific resource qualifier is winning.
  Check `values-night/`, `values-<lang>/`, `layout-land/`, `layout-sw600dp/`, and
  the density buckets for an override shadowing the file you edited.
- **Verify the edit actually landed** rather than trusting a clean build:
  `unzip -p build/x-signed.apk resources.arsc | strings | grep YourText`.
- **The baseline profile in `assets/dexopt/` is dropped** by an apktool rebuild.
  This costs some startup AOT optimization. Cosmetic — nothing breaks.
- **Don't touch `META-INF/`.** Signing regenerates it.
- **Keep a clean tree.** Re-decode to a pristine directory before starting a new
  change set, or keep `apktool_out/` under git so a bad edit is one `checkout`
  away.
