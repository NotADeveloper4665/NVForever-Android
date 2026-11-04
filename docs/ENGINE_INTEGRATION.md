# Engine integration status

## Source of truth

The upstream clean-room engine is tracked as a Git submodule at `upstream/NVForever`:

- Repository: https://github.com/RedZeroGotcha/NVForever-Android
- Branch tracked for updates: `main`
- Initial pinned revision: `4c8d0e76666e083fef742221beac3bfa8ea74c4a`
- License and legal rules: see `upstream/NVForever/LICENSE`, `LEGAL.md`, and `CONTRIBUTING.md`.

The submodule is source code only. It does not contain Fallout game data. Do not add proprietary game files, extracted assets, saves, or private test fixtures.

## Why this is not yet a runnable Android engine

The upstream Bevy workspace lives in `upstream/NVForever/engine` and pins Bevy to `=0.20.1`. The root format crates are separate from that Bevy workspace. The upstream README's documented executable is a desktop application. Adding the source tree to this repository does not make its desktop binary an Android activity.

The pinned engine uses Bevy `=0.20.1`. Bevy 0.20.1 documents Android GameActivity support and a `cargo-ndk` plus Gradle packaging flow; this gives the port a supported direction, but does not automatically adapt the existing desktop executable. See the [Bevy 0.20.1 Android build instructions](https://docs.rs/crate/bevy/0.20.1/source/examples/README.md) and [Bevy Android feature list](https://docs.rs/crate/bevy/0.20.1/source/docs/cargo_features.md).\n\nThe existing launcher is a regular AndroidX `ComponentActivity`. Bevy's GameActivity integration requires its own compatible activity/runtime entry point. The intended design is to keep the Kotlin launcher for SAF selection and diagnostics, then open a dedicated engine activity only after the native library, SAF bridge, and lifecycle are ready. Do not simply load the engine library into the current activity.\n\nBefore wiring an Android native library, the port must establish all of the following against the exact pinned Bevy revision:

1. The supported Android GameActivity entry point and native activity lifecycle, including the required Gradle/AndroidX GameActivity glue.
2. The Android target features and renderer backend required by Bevy 0.20.1.
3. The NDK/API level and native dependency compatibility for ARM64.
4. A bridge from Android's Storage Access Framework to the engine's VFS. A `content://` URI is not a POSIX path and must never be passed to path-based code as if it were one.
5. Android pause/resume, audio focus, controller input and rumble behavior.
6. A device smoke test on real ARM64 hardware.

Do not create a fake JNI library or enable the Launch button until it actually starts the engine. Do not copy the user's game folder into shared storage or write to it. Any required cache must be app-private, bounded, and read-only with respect to the source installation.

## Current implemented Android boundary

- Kotlin owns the SAF folder permission and validates the base master file.
- A cancellable importer stages the complete Data tree into app-private storage, verifies the copied master and BSA archives, and swaps the completed directory into place without writing to the original game installation.
- The selected tree is retained as a URI, not converted into a guessed filesystem path.
- The launcher can enumerate connected Android game controllers and observe key/axis activity for diagnostics.
- Controller diagnostics are not yet forwarded into Bevy gameplay actions.
- The engine submodule is available for source-level Android work but is not yet built into the APK.

## Validation gates

A successful Gradle build proves only that the Android shell packages. A successful Rust cross-build proves only that native code compiles. Engine startup, game-data loading, main-menu reachability and playable gameplay must each be tested and reported separately.

## Current hard blocker (do not paper over it)

The pinned upstream source contains no Android project or Android-specific entry point. Its game executable is currently a desktop binary target with a large `main.rs` that owns argument parsing, installation setup, Bevy window creation, and the game runtime. The existing `nvforever` package is not currently exposed as an Android `cdylib` library. Merely adding Bevy's `android-game-activity` Cargo feature or adding a GameActivity dependency to Gradle does not produce a loadable engine library.

The engine also has platform-specific assumptions that must be audited before ARM64 Android compilation: direct `wgpu`/Vulkan HAL and `ash` usage for window presentation and memory reporting, desktop window-mode/monitor APIs, engine-home/config discovery, schema paths relative to the checkout, and a path-based VFS. Android integration needs a real upstream engine refactor or a maintained fork, not a dummy JNI library.

The upstream engine is pinned as an external Git submodule. This repository's current GitHub write integration can change this Android wrapper repository, but cannot commit edits inside the upstream submodule's Git repository. To finish the native port, the engine source must first be moved to a writable maintained fork or vendored into this repository with its GPL-3.0-or-later notices and full source history preserved. Then the Android target can refactor the game entry point into a `cdylib`, enable Bevy 0.20.1's Android GameActivity backend, replace desktop-only startup/window code, pass app-private paths and schemas, and add a SAF/VFS bridge.

Until those steps are complete, the Launch control must remain disabled. A shell APK or a placeholder shared library is not a playable game.
