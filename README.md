# filmix-rebuild

Two halves:

- **`client/`** — **filmix-ng**, a new Kotlin/Compose client for the Filmix API,
  built for tablet, phone and Android TV.
- **`.claude/skills/apk-ui-mod/`** — the tooling used to decode and modify the
  original compiled APK, which is what made the rewrite possible.

The second came first. Decompiling the original established what its API
actually does, and that its UI could not be salvaged by restyling.

---

# filmix-ng

A ground-up client for the same backend the original app talks to.

| | |
|---|---|
| Stack | Kotlin · Compose · Material 3 · Media3 · Coil 3 · Retrofit · Room · Paging 3 |
| minSdk / targetSdk | 26 / 35 |
| Form factors | tablet (primary), phone, Android TV |
| Size | 2.8 MB, against the original's 5.9 MB |
| Theme | dark |

## Why rebuild rather than reskin

The original is a 2016-era AppCompat app, and its problem is structural rather
than cosmetic. The catalog is a `GridView` hard-cast in bytecode:

```smali
check-cast p1, Landroid/widget/GridView;
```

so it cannot become a lazy grid no matter how the resources are restyled. Same
for the `DrawerLayout` navigation, the v1 `ViewPager` detail tabs, and
`android.widget.VideoView` for playback — no adaptive streaming, no modern
codecs. The theme is applied imperatively, so system dark mode is ignored and
`values-night` sits empty.

On a 1181×738dp tablet the result is a stretched phone layout: the catalog
occupies about a quarter of the width and the rest is blank.

A resource-level reskin was tried and rejected before starting. The tooling
below is what proved it was a dead end.

## What it does

- **Home** — a featured hero plus horizontal rails, using the full tablet width.
- **Catalog** — paged grid, sortable by date, popularity or IMDB rating, and
  filterable by type, genre, country, year, voice-over and quality.
- **Detail** — backdrop, metadata as chips, related titles, and for series a
  season → voice-over → episode picker.
- **Player** — Media3/ExoPlayer with quality selection and resume positions.
- **Search** — debounced suggestions, with voice input where a recognizer
  is available.
- **Favourites, history, profile** — with device-code pairing.
- **Self-update** — checks a published manifest and installs in place.

Posters carry the vote count as a green/red badge, matching the original,
rather than a quality label.

## Install

Grab the APK from [`BUILD/`](BUILD/) and sideload it. Thereafter the app
updates itself: it reads [`BUILD/latest.json`](BUILD/latest.json) from this
repo, compares `versionCode`, verifies the download's SHA-256, and prompts.

Releases are signed with a key that is not in this repo, so a build you make
yourself is a separate app identity and cannot update one installed from
`BUILD/`.

## Build

No Gradle wrapper is committed; use a local Gradle 8.11+ and JDK 17.

```bash
cd client
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/path/to/android-sdk

gradle :app:assembleDebug      # → app/build/outputs/apk/debug/
gradle test                    # unit tests
```

Tests cover the parts that fail silently rather than loudly: stream-quality
template parsing, resume-key normalization, HTML unescaping in API text,
catalog filters, and version comparison.

`client/scripts/release.sh` builds a signed release, publishes it to `BUILD/`,
and writes the update manifest. It refuses to run on a dirty tree, so a release
is always reproducible from a commit.

## Notes on the API

Plaintext HTTP, and authentication is a device-code pairing flow — no request
signing, no certificate pinning. Field types do not always follow their names
(`last_episode` is a string like `"1-4"`), and text fields contain HTML
entities, so both are handled explicitly and covered by tests.

Premium titles return a short teaser to unpaired clients. That is the server's
subscription gate, and this client does not attempt to work around it.

---

# apk-ui-mod

Tooling for modifying the UI of a compiled Android APK — resolving resource IDs,
mapping on-screen elements back to the files that define them, and rebuilding
into an installable, signed APK.

Packaged as a [Claude Code](https://claude.com/claude-code) skill, but the three
scripts are plain bash and work standalone.

```
.claude/skills/apk-ui-mod/
├── SKILL.md              workflow: find the UI → change it → rebuild → sign
└── scripts/
    ├── resid.sh          resource ID ↔ name, both directions
    ├── find-ui.sh        text / activity / layout → the files behind it
    └── rebuild.sh        apktool b → zipalign → apksigner → verify
```

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

---

## Not in this repo

`.gitignore` excludes, deliberately:

| Excluded | Why |
|---|---|
| `filmixapp-2.2.13.apk` | Not ours to redistribute. `BUILD/*.apk` — our own releases — are tracked, since the updater fetches them from here |
| `apktool_out/`, `jadx_out/` | Decompiled proprietary code; also build output — reproducible from the APK in one command |
| `tools/` | Third-party binaries; jadx's jar exceeds GitHub's 100MB file limit |
| `*.keystore`, `*.jks`, `client/keystore/` | Private signing keys never belong in a repo |

`PROJECT.md` carries the analysis notes and workspace layout.

## Scope

A third-party client and modification tooling, for interoperability and for use
on hardware you own. It talks to an existing service using that service's own
API and adds no access you don't already have — the subscription gate is
enforced server-side and left alone. Don't use any of this to redistribute
someone else's app or to defeat licensing.

## License

MIT for the client, scripts and documentation here. The tools they drive
([apktool](https://github.com/iBotPeaches/Apktool),
[jadx](https://github.com/skylot/jadx)) carry their own licenses.
