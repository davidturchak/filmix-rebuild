---
name: filmix-client
description: Build, test, release, and debug the filmix-ng Jetpack Compose client in client/ — Gradle builds, unit tests, installing to the tablet or Android TV over adb, cutting a signed release into BUILD/ for the in-app updater, and the Filmix API. Use for any work on the new client: adding or changing screens, touching the network/data layer, chasing a bug on real hardware, verifying D-pad focus or TV layout, or shipping a new version.
---

# filmix-ng client

The repo holds two things. Know which one you are touching.

| | |
|---|---|
| `client/` | **The product.** Kotlin + Compose, built with Gradle. Nearly all work happens here. |
| `apktool_out/`, `jadx_out/` | The original `net.filmix.filmix` APK, decompiled. A **read-only oracle**, not a build target. |

The original app is how you answer "what does the real client send / how did
they parse this". It is still installed on the tablet as a behavioural
reference. For editing *it*, use the `apk-ui-mod` skill instead — but that is
legacy; the redesign replaced it.

## Layout

```
client/app                     nav host, MainActivity, WindowSizeClass + TV detection
client/core/model              domain types + the pure functions worth testing
client/core/network            Retrofit, device-param interceptor, DTOs
client/core/data               repositories, paging, resume positions
client/core/designsystem       theme, Dimensions, PosterCard, Rail, FocusChip
client/feature/{home,catalog,detail,player,search,library,profile}
BUILD/                         published APK + latest.json the updater reads
```

`:core:designsystem` and the repositories are shared verbatim by phone, tablet
and TV. Only the nav shell and a few density constants differ.

## Build and test

There is no Gradle wrapper. Export the toolchain first:

```bash
cd client
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk
G=/opt/gradle-8.11.1/bin/gradle

$G :app:assembleDebug --console=plain
$G test --console=plain          # unit tests across all modules
```

Gradle prints thousands of lines. Filter to what matters —
`| grep -E 'e: |FAILED|BUILD'` — but never filter an **install** (see below).

Tests cover the pure functions that break silently: quality-template parsing,
resume-key normalization, HTML unescaping, catalog filters, version compare,
and a payload contract test. Add to these rather than reaching for
instrumentation.

## Devices

Two real targets. Both must be reconnected before use.

```bash
D=$(client/scripts/adb-target.sh) || exit 1   # Teclast tablet — port ROTATES
TV=192.168.1.195:5555                          # TCL Google TV, fixed port
adb connect "$TV"
```

The tablet's wireless-debugging port changes on every reboot and the link drops
on sleep. Always resolve it through `adb-target.sh`; a stale port hangs for
minutes instead of failing.

### Never filter install output

```bash
adb -s "$TV" install -r app/build/outputs/apk/debug/app-debug.apk   # read it all
```

`adb install` prints `Success` on the last line even when an earlier line says
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Piping through `tail -1` once hid a
failed install through several rounds of testing against a stale build. Read
the whole output, or grep for both `Success` and `Failure`.

### Signature mismatch costs the user their login

Debug-signed and release-signed builds cannot replace each other. Switching
requires `adb uninstall`, which **wipes the pairing token** — the user must
re-pair on filmix.biz. The release build is not debuggable, so the token cannot
be backed up and restored. **Ask before uninstalling.**

## Releasing

```bash
client/scripts/release.sh -n "what changed"
```

Builds a signed release, copies it to `BUILD/`, and writes `latest.json`
(versionCode, versionName, commit, apkUrl, sha256, notes). It refuses to run on
a dirty tree, because a release must be reproducible from a commit. Bump
`appVersionName` in `client/app/build.gradle.kts` first; versionCode comes from
the git commit count automatically.

Then commit `BUILD/` and push — the updater fetches `latest.json` from
raw.githubusercontent.com, so **a release is not live until pushed**.

**`client/keystore/` is gitignored and irreplaceable.** Lose it and no existing
install can ever be updated in place again; every user has to uninstall and
re-pair. Back it up outside the repo.

`git push` is denied by the user's settings. Do not burn attempts on it — ask
them to run `! git push`.

## The API

Base `http://filmixapp.cyou`, plaintext HTTP. Auth is device-code pairing:
`POST /api/v2/token_request` returns `{user_code, code}`; `code` is stored and
sent as `user_dev_token` while the user types `user_code` on the website. No
HMAC, no pinning.

**Field names lie about their types.** `last_episode` is a String ("1-4"), not
an Int — typing it wrong made an entire catalog page fail to parse and silently
dropped a rail from the home screen. `curl` the endpoint and look at the actual
JSON before modelling it.

Text fields contain **HTML** (`<br />`, `&#233;`). Run them through
`Html.toPlainText`, which exists and is tested.

Unpaired clients get an **11-second teaser** for every premium title, plus a
subscription popup. That is the server's PRO gate working correctly — not a
playback bug. Three unrelated titles returning an identical ETag confirmed it.

## Android TV

TV is detected via `UiModeManager.currentModeType`, which flows into
`FilmixTheme(isTv = …)` and swaps `TouchDimensions` for `TvDimensions` through
`LocalDimensions`. Never hardcode sizes — read `LocalDimensions.current`.

Every focusable needs a visible focus ring; use `FocusChip` rather than a raw
`FilterChip`.

**Voice search must drive `SpeechRecognizer` directly**, with `RECORD_AUDIO`
requested at runtime. `ACTION_RECOGNIZE_SPEECH` looks portable and does nothing
useful here: it opens the TV search overlay with a dead microphone. Confirm it
works by looking for these in logcat, not by watching the screen:

```
RecognitionServiceImpl: logStartListening: callingApp: dev.turchak.filmixng
RecognitionClient: #onResults withSpeech: true
```

Pass the **device locale**; a hardcoded language the recognizer lacks data for
captures audio and returns nothing, looking exactly like a broken microphone.

### Do not diagnose accessibility from uiautomator

`uiautomator dump` prints Compose's **unmerged** semantics tree. Clickable nodes
show empty `text` and `content-desc` — including plain Material `Button`s — and
labels appear as separate child nodes. This is a tooling artifact, not missing
labels. Anything read this way needs confirming with a real screen reader
before it counts as a finding.

## Verifying on hardware

```bash
adb -s "$TV" shell am force-stop dev.turchak.filmixng
adb -s "$TV" shell am start -n dev.turchak.filmixng/net.filmix.client.MainActivity
sleep 12
adb -s "$TV" logcat -d -b crash | tail
adb -s "$TV" exec-out screencap -p > shot.png
```

Drive the D-pad by **reading focus back between presses**, not by guessing press
counts — blind sequences walk out of the app into the Google TV launcher and
you end up inspecting the wrong screen:

```bash
adb -s "$TV" shell uiautomator dump /sdcard/f.xml
adb -s "$TV" shell cat /sdcard/f.xml | tr '<' '\n' | grep 'focused="true"'
```

The user may be using the TV while you test. If the screen shows something you
did not do, stop driving it.

## Conventions

Russian is the base locale (`values/`), Ukrainian is a full translation
(`values-uk`). Never introduce an English default. Keep both in step.

DI is a hand-rolled `AppGraph`, not Hilt. Dark theme only.
