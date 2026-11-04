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

Before wiring an Android native library, the port must establish all of the following against the exact pinned Bevy revision:

1. The supported Android entry point and native activity lifecycle.
2. The Android target features and renderer backend required by Bevy 0.20.1.
3. The NDK/API level and native dependency compatibility for ARM64.
4. A bridge from Android's Storage Access Framework to the engine's VFS. A `content://` URI is not a POSIX path and must never be passed to path-based code as if it were one.
5. Android pause/resume, audio focus, controller input and rumble behavior.
6. A device smoke test on real ARM64 hardware.

Do not create a fake JNI library or enable the Launch button until it actually starts the engine. Do not copy the user's game folder into shared storage or write to it. Any required cache must be app-private, bounded, and read-only with respect to the source installation.

## Current implemented Android boundary

- Kotlin owns the SAF folder permission and validates the base master file.
- The selected tree is retained as a URI, not converted into a guessed filesystem path.
- The launcher can enumerate connected Android game controllers and observe key/axis activity for diagnostics.
- Controller diagnostics are not yet forwarded into Bevy gameplay actions.
- The engine submodule is available for source-level Android work but is not yet built into the APK.

## Validation gates

A successful Gradle build proves only that the Android shell packages. A successful Rust cross-build proves only that native code compiles. Engine startup, game-data loading, main-menu reachability and playable gameplay must each be tested and reported separately.
