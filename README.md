# Abyss Anmap

Android GUI for a locally executed Nmap build with the complete supplied Nmap
NSE catalogue and the supplied Vulscan 2.1 offline database bundle.

## What it does

- Runs Nmap **on the phone**; there is no cloud scanner, account, telemetry, or
  external backend.
- Packages every Nmap script from the supplied `scripts/` directory, its
  `script.db`, and the complete supplied `nselib` directory.
- Adds a purpose-built offline Vulscan profile using
  `scripts/vulscan/vulscan.nse` and its bundled CSV databases.
- Uses unprivileged TCP connect scans (`-sT --unprivileged`) so it works without
  root. Raw-packet scan types are deliberately not requested by the GUI.
- Offers a free NSE selector that accepts normal Nmap script names and category
  selectors such as `default,safe`, `http-title`, or `vuln`, plus an optional
  `--script-args` field for scripts that require arguments.
- Keeps output in the app and supports copying it to the clipboard.

The supplied Vulscan database snapshot is dated 2019. Findings are potential
version correlations, not verified vulnerabilities.

## Build

The project targets **arm64-v8a** and Android 8.0+.

Prerequisites:

- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Android NDK 27.1.12297006
- Gradle 8.10.2 (Android Studio can supply it)

From the repository root:

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.1.12297006"
chmod +x native/prepare-upstream.sh
native/prepare-upstream.sh
chmod +x native/build-nmap.sh
native/build-nmap.sh
gradle --no-daemon :app:assembleDebug
```

The resulting APK is `app/build/outputs/apk/debug/app-debug.apk`.

## CI build

The included GitHub Actions workflow installs the SDK/NDK, fetches the exact
official Nmap and Vulscan Git revisions represented by the supplied archives,
stages every NSE script and the full Vulscan bundle, cross-compiles Nmap, builds
the APK, and uploads it as the `Abyss-Anmap-debug-apk` artifact. It runs on
`main`/`master`, pull requests, or manually from **Actions**.

## Source layout

- `native/prepare-upstream.sh` — fetches the two exact official upstream
  revisions named in the supplied archives and prepares the generated sources
  and runtime assets
- `third_party/nmap` — generated complete supplied Nmap source tree
- `third_party/vulscan` — generated complete supplied Vulscan source/database
  tree
- `app/src/main/assets/nmap-data` — generated Nmap runtime data, full NSE
  catalogue, `nselib`, and Vulscan files copied from those sources
- `native/build-nmap.sh` — reproducible arm64 Android NDK build

## Licensing and attribution

Read [NOTICE.md](NOTICE.md), [LICENSE](LICENSE), and
[LICENSES/VULSCAN-GPL-3.0.txt](LICENSES/VULSCAN-GPL-3.0.txt) before sharing an
APK or modifying the project. Nmap is a registered trademark of Nmap Software
LLC; the name is used here only to identify the bundled scanner.
