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
client/core/designsystem       theme, Dimensions, Buttons, PosterCard, Rail, FocusChip, FocusReturn
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
resume-key normalization, HTML unescaping, catalog filters, filter previews,
version compare, and a payload contract test. Add to these rather than reaching
for instrumentation — and when the logic you want to pin down lives inside a
composable, lift it into `:core:model` first, the way `previewOptions` was.

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

Installing a *released* APK over your own working build fails with
`INSTALL_FAILED_VERSION_DOWNGRADE` — versionCode is the commit count, so yours
is ahead. `adb install -r -d` allows it. Note the shape of that failure: the
error line comes *before* `Performing Streamed Install`, so `tail -1` reports
the reassuring half of a failed install.

### Signature mismatch costs the user their login

Debug-signed and release-signed builds cannot replace each other. Switching
requires `adb uninstall`, which **wipes the pairing token** — the user must
re-pair on filmix.biz. The release build is not debuggable, so the token cannot
be backed up and restored. **Ask before uninstalling.**

## Releasing

Release notes live in `CHANGELOG.md`, not on the command line. Write the
bullets under `## Unreleased` and commit them first; `release.sh` refuses to
build without them, and `scripts/changelog.py` stamps the heading with the
version and build number once the build reports what they are.

```bash
# 1. bump appVersionName in client/app/build.gradle.kts
# 2. write bullets under "## Unreleased" in CHANGELOG.md
# 3. commit both
client/scripts/release.sh
# 4. commit CHANGELOG.md (now stamped) together with BUILD/
```

Builds a signed release, copies it to `BUILD/`, and writes `latest.json`
(versionCode, versionName, commit, apkUrl, sha256, notes, changelog). It
refuses to run on a dirty tree, because a release must be reproducible from a
commit. versionCode comes from the git commit count automatically.

`latest.json` is read by **every version ever released**, and a client that
cannot parse it can never update itself again. So add keys, never change one:
`notes` in particular must stay a string, because turning it into an array
throws on every install in the field. `ManifestContractTest` in `:core:data`
pins this against a copy of the old DTO — it fails loudly if you try.

Then commit `BUILD/` and push — the updater fetches `latest.json` from
raw.githubusercontent.com, so **a release is not live until pushed**. Pushed is
not the same as served: that URL is cached `max-age=300`, so for up to five
minutes the app still sees the previous release and honestly reports
«Установлена последняя версия». Check `curl -sSI … | grep source-age` before
suspecting the app.

`BUILD/` keeps **only the current release** — `release.sh` deletes the previous
APK, because each one is ~3MB of binary that would sit in git history forever.
So a direct link to `filmix-ng-<version>.apk` 404s the moment you ship the next
one. The stable thing to hand anybody is `latest.json`, which always names the
current `apkUrl`.

**`client/keystore/` is gitignored and irreplaceable.** Lose it and no existing
install can ever be updated in place again; every user has to uninstall and
re-pair. Back it up outside the repo.

`git push` is denied by the user's settings. Do not burn attempts on it — ask
them to run `! git push`.

## Launcher icon and banner

Four sets of artwork, all **generated** rather than hand-drawn:

| | |
|---|---|
| `mipmap-*/ic_launcher_foreground.png` | adaptive foreground — 108/162/216/324/432 |
| `mipmap-*/ic_launcher_background.png` | adaptive background — same sizes |
| `mipmap-*/ic_launcher{,_round}.png` | legacy pre-API-26 — 48/72/96/144/192 |
| `drawable-xhdpi/banner.png` | Leanback banner — 320x180 |

Adaptive layers are **2.25x the legacy size** at every density.

The palette is Filmix's own, read out of
`filmix.gg/templates/Filmix/media/public/css/main.css` rather than guessed:
`#F26739` primary (98 uses there, and the favicon's dominant colour), `#F05521`
deep, on grounds `#292C33` / `#1A1B22`.

**The adaptive background is a bitmap, not a colour.** It used to be
`@color/ic_launcher_background`, a flat orange. The ground is now a gradient, so
`ic_launcher.xml` points at `@mipmap/ic_launcher_background`. The colour
resource still exists holding the dark base, but **nothing references it** —
editing it changes nothing on screen, which is the wrong thing to discover
halfway through debugging a tile.

**The foreground must fit the 66/108dp safe circle**, or some launcher masks
clip it and others do not. Fit it by measuring, not by eye: scan the alpha for
the furthest opaque pixel from centre and scale so that radius lands on
`(66/108)/2 x canvas`. A wordmark is far more exposed to this than a single
glyph, being wider.

Which artwork a TV actually shows depends on the launcher, and the two disagree:
the **Leanback** launcher draws the banner and never the square icon, while
**Google TV** — what the TCL runs — draws the adaptive icon. Both are declared,
on the application *and* the activity. So "the tile is wrong on TV" does not
tell you which file to edit; check which launcher you are looking at first.

### Verifying icons actually shipped

Release builds **shorten resource paths**: `res/mipmap-xxxhdpi/ic_launcher.png`
becomes `res/Gx.png`. Grepping a release APK for the resource name finds nothing
and proves nothing. Match on dimensions instead, then composite the two adaptive
layers to see what a launcher will draw — the foreground is the mostly
transparent one of the pair:

```bash
unzip -o -q BUILD/filmix-ng-*.apk 'res/*.png' -d /tmp/apkcheck
# 432x432 appears twice (foreground + background); 320x180 is the banner
```

**Launchers cache icons hard.** After an update the old tile often persists
until a reboot — that is the launcher, not a failed build. The banner tends to
refresh before the square icon, so a new banner beside a stale icon is expected
and is not evidence of a bad install.

### Image tooling on this host

There is no ImageMagick and no SVG renderer — no `convert`, `magick`,
`rsvg-convert`, `inkscape`, `cairosvg`. **Pillow only**, so draw geometry
directly and supersample about 4x for antialiasing. Fonts are DejaVu and Lato
(`/usr/share/fonts/truetype/lato/`, Black through Hairline).

To show the user a rendered image, POST it to the uploader running on this host
and hand them the URL it answers with:

```bash
curl -sS -F "file=@icon.png" http://192.168.1.223:8080/upload
# {"url":"https://files.ku4er.net/f/<id>.png"}
```

Render a contact sheet at real launcher sizes (96/72/48) before asking the user
to judge a design. Type that reads at 512dp can be a smudge at 48px, and that is
only visible if you actually render it.

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

Stream URLs are **signed and expiring**: `https://nl205.cdnsqu.com/s/<token>/
<Folder>/<file>` — the `/s/<token>/` segment rotates between fetches of the
same post (within ~10 minutes, and regardless of device identity), and the
host node can move too. Only the trailing `<folder>/<file>` is stable. Never
persist or compare full stream URLs; `StreamLink.resumeKey` keys on the stable
tail, and keying on anything more once silently orphaned every stored resume
position on the next refetch of the post.

## Android TV

TV is detected via `UiModeManager.currentModeType`, which flows into
`FilmixTheme(isTv = …)` and swaps `TouchDimensions` for `TvDimensions` through
`LocalDimensions`. Never hardcode sizes — read `LocalDimensions.current`.

### Focus is the whole TV experience

Material renders focus as a 10% state layer of the content colour, which is no
cue at three metres — and on an accent-filled button the accent ring *is* the
fill, so it shows nothing at all. `:core:designsystem` owns the ringed versions:
`PrimaryButton`, `OutlinedButton`, `TextButton`, `IconButton`,
`FilledTonalIconButton`, `FocusChip`. They deliberately carry Material's names,
so a call site reads unchanged and `import androidx.compose.material3.TextButton`
outside the design system is a greppable mistake. Filled buttons ring in
`onPrimary`; everything else takes the accent.

Two things about `focusRing` that cost real time:

- Its 1.06 lift is drawn, not laid out. On an item as wide as its container — a
  nav rail item, a tab in a two-tab row — the outer edge lands past the screen
  and is silently clipped. Pass `scaleWhenFocused = 1f` there.
- Directional focus search **will not descend into the focus target it is
  leaving**. A control inside another component's layout — a `TextField`'s
  trailing icon — is unreachable by RIGHT even though TAB finds it, and a
  right-aligned control alone in a header row is reachable only from whichever
  chip below shares its vertical beam. Make such controls siblings, or put them
  in the same column as their neighbours.

The rail selects on focus: landing on a tab opens it, with no centre press,
because a remote has no cheap commit gesture. Entering the rail is pinned to the
current tab through `focusProperties { enter = … }` — which must *precede* the
focus target `focusGroup()` adds, or it configures the items instead and does
nothing at all. BACK walks back to Home and only leaves the app from there.

Compose drops focus whenever the focused node leaves composition, which happens
more than you would expect: a tab's content is replaced while detail is open, a
button vanishes with the state that showed it. `FocusReturn` restores the cursor
into a list; elsewhere hand focus somewhere deliberate rather than letting it
fall back to whatever is first.

### While the on-screen keyboard is up, it owns the D-pad

Not the app. Presses die in the keyboard's window, so a text field that has just
regained focus — which re-opens the keyboard — looks frozen: nothing navigates,
and any key handling you add never runs. BACK closes the keyboard and navigation
resumes.

That is Android, not this client, and it explains most of what looks like "the
remote stopped working" on the search screen. Check
`dumpsys input_method | grep mInputShown` before debugging anything else.
Suppressing the keyboard on focus does fix the navigation and is not worth it:
centre can then never re-open it and the field becomes untypable.

A focused text field also claims up, down, left and right for its caret even
when it is single-line and the caret has nowhere to go. `SearchScreen` previews
those keys and moves focus itself, which is the only reason the results and the
clear button are reachable.

### Playback controls need the view to hold focus

`PlayerView` reveals its controls from its own `dispatchKeyEvent`, so a press
only counts if that view holds Android focus — and nothing inside `AndroidView`
gives it any. Worse, the controls auto-show when playback starts and take focus
for their buttons; when the timeout hides them, focus goes with them and the
window is left with *nothing* focused, so the controls become unsummonable
partway through a film. `PlayerScreen` claims focus once attached, takes it back
from a `ControllerVisibilityListener` whenever they close, and summons them from
Compose on the first press as a backstop.

### Voice search

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

It does not expose Media3's controller at all — buttons, seek bar and timecode
are absent from the dump, so a node count reads "no controls" while they sit
plainly on screen. Verify playback UI from a screenshot, or a pixel probe on the
timecode strip, never from the tree.

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

### The TV plays through MX Player Pro, not our player

The user's Настройки set the video player to **MX Player Pro**, so every
playback launches MX via the external-player intent — the fullscreen player
you land in is MX (the `HW` badge top-right is MX's decoder toggle), not
`PlayerScreen`, which never runs. A whole debugging session was lost to
misreading MX as our player. Consequences:

- **Resume positions save only when MX exits cleanly** and its activity result
  reaches a *live* PlaybackHost composition. If our activity is recreated
  while MX is foregrounded — TCL's FreezeManager freezes and evicts the
  backgrounded app within minutes — the result is dropped and that session's
  position is silently lost. This is the documented external-player tradeoff,
  not a bug in the resume store.
- **Media keys (`input keyevent 90`, the remote's ⏪⏩) route to the media
  button session owner** — MX if it is running, even in the background. Keys
  you inject can seek an invisible MX session while you watch our app do
  nothing. `dumpsys media_session` shows who owns the media-button session;
  `am force-stop com.mxtech.videoplayer.pro` clears it.
- To exercise the internal player deliberately, switch Настройки → Видеоплеер
  to «Встроенный», test, and **switch it back to MX Player Pro after** — it is
  the user's preference. With the internal player, seeks land only while the
  controller is up, and the 5s progress ticks plus the dispose save all work
  (verified on hardware).

## Cross-screen state

Screens are activity-scoped and there is no nav backstack, so a ViewModel built
before something happened elsewhere never hears about it — it keeps whatever it
resolved on first open for the life of the process. Two signals exist for that,
both in `:core:data`; reach for the same shape rather than reloading on every
visit:

- `SessionState.linked` — whether the device is paired. Pairing cannot be seen
  in the token (`token_request` issues one up front; the account is attached
  later, on the website), so `AuthRepository.fetchProfile` publishes the answer
  and the library screens reload when it disagrees with what they show.
- `LibraryRepository.revision` — bumped by its own favourite and watch-later
  toggles, so favouriting on the detail screen refreshes the library list.

Failures there must not fold into an empty list: `favourites`, `history` and
`deferred` all answer `[]` when unpaired, so a dead request and an empty library
are indistinguishable unless the state says which.

## Conventions

All UI text is Russian. **Never introduce an English default.**

It lives as inline literals in the Kotlin, not in `strings.xml` — the only
`strings.xml` holds the 14 nav labels, and no feature module has a `res/`
directory. (`values-uk` belongs to the *original* decompiled APK, not this
client; there is nothing to keep in step.) Follow the neighbouring call sites
rather than introducing resources for one screen.

DI is a hand-rolled `AppGraph`, not Hilt. Dark theme only.
