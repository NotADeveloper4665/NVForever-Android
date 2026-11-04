# NVForever Android

Android launcher and native runtime port for [NVForever](https://github.com/NotADeveloper4665/NVForever-Android-Engine), the clean-room Rust/Bevy reimplementation of Fallout: New Vegas.

**Status: Android native integration is wired in source; ARM64 cross-build and APK CI validation are in progress. This is not yet a verified playable Android release.**

## Current implementation

- Kotlin launcher with Android Storage Access Framework folder selection and persisted read-only access.
- Accepts the game root or its `Data` directory.
- Validates `FalloutNV.esm` and BSA archives without modifying the selected installation.
- Cancellable import of the complete `Data` tree into app-private storage, with free-space preflight, progress, verification, and recovery-aware directory swap.
- Separate AndroidX GameActivity host for the Bevy runtime.
- Runtime paths are passed before native startup: imported game data, NVForever's writable home, and bundled ESM/NIF/script schemas.
- Engine source is maintained in [NotADeveloper4665/NVForever-Android-Engine](https://github.com/NotADeveloper4665/NVForever-Android-Engine) and pinned as `upstream/NVForever`.
- CI cross-compiles the Rust engine for ARM64 before packaging the debug APK.

## Still to validate or implement

- Successful ARM64 native engine compilation and APK packaging on the current revision.
- Actual startup on a physical Android device, Vulkan renderer compatibility, game-data/schema loading, and reaching the main menu.
- Touchscreen controls and complete physical-controller gameplay mapping/rumble.
- Device-specific performance, thermal, memory, and audio-focus testing.
- The upstream engine's remaining gameplay and feature gaps; Android compilation does not make upstream gameplay feature-complete.

A successful Gradle build is not proof of gameplay. Engine compilation, engine startup, data loading, main-menu reachability, and playable gameplay are separate acceptance gates.

## Build locally

Requires JDK 17, Android SDK Platform 35, Android NDK 27.2.12479018, Rust stable, and `cargo-ndk`.

```bash
git clone --recurse-submodules https://github.com/NotADeveloper4665/NVForever-Android.git
cd NVForever-Android
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
cd upstream/NVForever/engine
cargo ndk -t arm64-v8a -P 26 -o ../../../app/src/main/jniLibs build --release --locked --lib -p nvforever
cd ../../..
gradle --no-daemon :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Legal and content policy

This project distributes no Fallout: New Vegas game data, assets, executable, DLC, saves, or extracted content. Users must supply game data from a copy they are entitled to use. The Android importer reads the selected installation and stages a private copy; it never modifies the source installation.

NVForever is an independent clean-room project and is not affiliated with Bethesda, ZeniMax, Microsoft, or Obsidian. Preserve the engine fork's GPL-3.0-or-later license and notices. Never commit proprietary game data or private parity fixtures.

See [docs/ANDROID_DATA_STAGING.md](docs/ANDROID_DATA_STAGING.md), [docs/ENGINE_INTEGRATION.md](docs/ENGINE_INTEGRATION.md), and [docs/CONTROLLER_INPUT.md](docs/CONTROLLER_INPUT.md).
