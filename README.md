# filmix-ng releases

Signed release builds of **filmix-ng**, an Android client for Filmix for phones,
tablets and Android TV. This repository holds only the published artefacts:

- `BUILD/filmix-ng-<version>.apk` — the current release, signed with the
  project's release key. Only the latest APK is kept.
- `BUILD/latest.json` — the manifest the app's built-in updater reads on launch.
  It names the version, the APK's URL and its sha256, and carries the release
  notes shown in the update prompt.

Install the APK once by sideloading it; from then on the app offers updates
itself. The source code lives in a separate, private repository.
