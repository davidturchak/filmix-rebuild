# filmix-rebuild

Tooling for modifying the UI of a compiled Android APK — resolving resource IDs,
mapping on-screen elements back to the files that define them, and rebuilding
into an installable, signed APK.

Packaged as a [Claude Code](https://claude.com/claude-code) skill, but the three
scripts are plain bash and work standalone.

## What's here

```
.claude/skills/apk-ui-mod/
├── SKILL.md              workflow: find the UI → change it → rebuild → sign
└── scripts/
    ├── resid.sh          resource ID ↔ name, both directions
    ├── find-ui.sh        text / activity / layout → the files behind it
    └── rebuild.sh        apktool b → zipalign → apksigner → verify
PROJECT.md                analysis notes for the APK this was built against
```

No APK, decompiled source, or signing key is committed — see
[Not in this repo](#not-in-this-repo).

## Why

Published Android skills split into two camps, and neither covers this:
reverse-engineering skills stop at analysis (decompile, extract APIs, Frida),
and Android dev skills assume a Gradle source tree. Modifying the UI of an app
you only have as a binary falls between them.

The hard part isn't editing XML — it's that compiled code addresses views only
as bare hex constants:

```smali
const v3, 0x7f09006a
invoke-virtual {p0, v3}, ...->findViewById(I)Landroid/view/View;
```

`resid.sh` resolves those against `res/values/public.xml`, which is what
connects code to UI:

```console
$ resid.sh 0x7f09006a
id/app_name = 0x7f09006a

$ resid.sh -u app_version          # every smali and xml site that uses it
id/app_version = 0x7f09006b
smali references to 0x7f09006b:
smali_classes2/net/filmix/filmix/AboutActivity.smali:424:    const v4, 0x7f09006b
xml references to @app_version:
res/layout/activity_about.xml
res/layout-v21/activity_about.xml
```

`find-ui.sh` works from whichever end you have — visible text, an activity, or
a layout:

```console
$ find-ui.sh -a AboutActivity      # → layout, theme, resolved resource ids
$ find-ui.sh -l activity_about     # → ids with hex, and what inflates it
$ find-ui.sh -t "Настройки"        # → string resources and their users
```

## Usage

Requires **JDK 17+**, **apktool**, **zipalign**, and **apksigner**
(`android-sdk-build-tools`, or `apt install apksigner zipalign`).

```bash
# decode
java -jar apktool.jar d -f -o apktool_out app.apk

# find what to change, edit it under apktool_out/res or apktool_out/smali*
./scripts/find-ui.sh -t "Some visible text"

# rebuild, sign, verify
./scripts/rebuild.sh                 # → build/apktool_out-signed.apk
```

`rebuild.sh` generates a debug keystore on first run and fails loudly at
whichever step breaks. Point the scripts elsewhere with `-d`, or set
`APKTOOL_OUT`, `APKTOOL_JAR`, `APK_KEYSTORE`.

Rebuilt APKs are signed with *your* key, so Android treats the result as a
separate app: uninstall the original first, and it won't inherit its data.

## Not in this repo

`.gitignore` excludes, deliberately:

| Excluded | Why |
|---|---|
| `*.apk` | Not ours to redistribute |
| `apktool_out/`, `jadx_out/` | Decompiled proprietary code; also build output — reproducible from the APK in one command |
| `tools/` | Third-party binaries; jadx's jar exceeds GitHub's 100MB file limit |
| `*.keystore` | Private signing keys never belong in a repo |

## Scope

Interoperability and modification of an app on hardware you own. Don't use it to
redistribute someone else's app or defeat licensing.

## License

MIT for the scripts and documentation here. The tools they drive
([apktool](https://github.com/iBotPeaches/Apktool),
[jadx](https://github.com/skylot/jadx)) carry their own licenses.
