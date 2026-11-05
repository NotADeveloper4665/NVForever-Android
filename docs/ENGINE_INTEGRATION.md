# Engine integration status

## Source of truth

The maintained engine fork is:

https://github.com/NotADeveloper4665/NVForever-Android-Engine

It is pinned as the `upstream/NVForever` Git submodule in this repository. Keep Android launcher/packaging changes here and Rust/Bevy runtime changes in the engine fork. Preserve GPL-3.0-or-later licensing, clean-room boundaries, and all upstream notices. Do not add proprietary game data, extracted assets, saves, or private parity fixtures.

## Implemented integration

### Engine repository

- The `nvforever` package now exposes a shared `cdylib`/Rust library target for Android and retains a desktop executable wrapper.
- Bevy 0.20.1's Android GameActivity backend is explicitly enabled in the engine workspace.
- A dedicated `#[bevy_main]` entry point is present for Android; the desktop wrapper calls the same runtime function.
- Android runtime paths can be supplied through `NVFOREVER_DATA`, `NVFOREVER_HOME`, `NVFOREVER_ESM_SCHEMA`, and `NVFOREVER_NIF_SCHEMA`.
- Android startup uses Bevy's mobile Winit settings, starts borderless fullscreen, and disables MSAA pending device validation.
- Active audio sinks pause/resume on Bevy's Android activity lifecycle messages.

### Android application

- The Kotlin launcher keeps SAF permissions and import diagnostics separate from the game runtime.
- A dedicated `EngineActivity` extends AndroidX GameActivity; it sets process environment paths before the native Bevy entry point starts.
- The app bundles the engine's ESM/NIF/script schema files and stages them under app-private storage.
- The launch action is enabled only when a verified imported `FalloutNV.esm` exists.
- The Android workflow cross-compiles the engine for `aarch64-linux-android`, packages `libnvforever.so`, and assembles the APK.

## Validation still required

1. The ARM64 Rust cross-build must complete successfully with the exact pinned engine revision.
2. Gradle must package the native library and GameActivity into an APK.
3. Install and launch on a physical ARM64 device (Pixel 9a is the initial target).
4. Confirm Vulkan/WGPU device creation and surface recreation through pause/resume.
5. Confirm the engine reads the imported Data directory and bundled schemas, then reaches the main menu.
6. Verify save/config/log paths stay inside app-private storage.
7. Implement and test touch controls, physical gamepad action mapping, reconnect behavior, and rumble.
8. Profile memory, frame pacing, thermal behavior, audio focus, and long-session stability.

A successful cross-compile proves only that the native library compiles. A successful APK build proves only that the app packages. Neither proves runtime startup or playable gameplay.

## Storage contract

The SAF URI is never passed to Rust as a POSIX path. The launcher copies the selected Data tree to `filesDir/game-install/Data`, then passes the synthetic install root (`filesDir/game-install`) as `NVFOREVER_DATA`. NVForever's writable home is the sibling `filesDir/nvforever`; this separation is required by `fnv_install::check_separation`. Bundled schemas are copied to `filesDir/engine-schemas`. The selected original game installation remains read-only and untouched. The importer validates all eight non-empty files in `fnv_install::REQUIRED_DATA_FILES` before enabling launch.

## Known upstream limitations

This Android work does not complete unfinished upstream gameplay systems or guarantee full Fallout: New Vegas parity. The engine's current maturity, implemented gameplay systems, renderer assumptions, and any missing features must be assessed separately from Android build success. Do not advertise the app as fully playable until a physical-device test reaches a stable gameplay loop.
