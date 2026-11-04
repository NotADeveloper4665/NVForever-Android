# NVForever Android

An Android port workstream for [NVForever](https://github.com/RedZeroGotcha/NVForever-Android), the clean-room Rust reimplementation of Fallout: New Vegas.

**Status: Android shell and upstream source integration in progress. This is not yet a playable game APK.**

## Current implementation

- Native Kotlin Android launcher.
- Android Storage Access Framework folder picker with persisted read permission.
- Accepts either the game root or its `Data` directory.
- Validates `FalloutNV.esm` and reports detected BSA archives without modifying the selected installation.
- Imports the complete `Data` tree to app-private storage with free-space preflight, progress reporting, cancellation, verification, and an atomic directory swap. The original installation remains read-only.
- Detects Android gamepads and observes standard key/axis events for diagnostics. Events are not consumed.
- Upstream engine source is pinned as a Git submodule at `upstream/NVForever`.

## What is not yet implemented

- The Bevy engine is not yet built as an Android native library; upstream is still a desktop binary and needs a maintained writable fork/refactor.
- The staged app-private Data directory is not yet passed to NVForever's VFS or native runtime.
- Controller events are not yet mapped into gameplay actions or rumble.
- No engine scene, main menu, or gameplay has been launched on Android.
- A successful debug APK build is only a shell build, not proof of engine integration.

See [docs/ENGINE_INTEGRATION.md](docs/ENGINE_INTEGRATION.md), [docs/CONTROLLER_INPUT.md](docs/CONTROLLER_INPUT.md), and [docs/android-port-plan.md](docs/android-port-plan.md).

## Build locally

Requires JDK 17 and Android SDK Platform 35:

```bash
git clone --recurse-submodules https://github.com/NotADeveloper4665/NVForever-Android.git
cd NVForever-Android
gradle --no-daemon :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Legal and content policy

This project distributes no Fallout: New Vegas game data, assets, executable, DLC, saves, or extracted content. Users must supply game data from a copy they are entitled to use. The Android picker retains read permission only; the selected game installation is never modified.

NVForever is an independent clean-room project and is not affiliated with Bethesda, ZeniMax, Microsoft, or Obsidian. Preserve the upstream GPL-3.0-or-later license and notices in `upstream/NVForever`. Never commit proprietary game data or private parity fixtures.

See [docs/ANDROID_DATA_STAGING.md](docs/ANDROID_DATA_STAGING.md) for the SAF import contract and [docs/ENGINE_INTEGRATION.md](docs/ENGINE_INTEGRATION.md) for the remaining native-engine blocker.
