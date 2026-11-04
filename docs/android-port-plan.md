# Android port plan

## Progress update

- M0 Android shell: Gradle debug packaging passed in GitHub Actions on the initial scaffold commit. The Kotlin launcher now also has SAF validation and controller diagnostics; the latest revisions are being rebuilt.
- Upstream source: pinned as a Git submodule at `upstream/NVForever`. Clone with `--recurse-submodules`.
- M1 Android engine build: not complete. The upstream Bevy workspace is pinned to Bevy 0.20.1, but the desktop executable has not been converted to an Android GameActivity entry point or cross-compiled.
- M2 SAF: persistent read-only tree selection and base-master/BSA detection are implemented in the Kotlin shell. The engine VFS does not yet consume the SAF tree.
- M3–M5: not complete; no engine scene or gameplay has been run on Android.

A green shell build must not be described as an engine build or playable APK.

## Baseline

The upstream NVForever repository is a clean-room Rust/Bevy implementation. Its engine is a substantial desktop codebase, not an Android-ready application. The Android port must preserve the upstream clean-room and no-game-data rules.

The current engine runtime has desktop-specific assumptions that must be audited before attempting a complete Android build:

- The game-folder first-run dialog is Windows-only and uses `rfd`.
- The executable's entry point is a desktop Bevy application.
- The engine directly depends on Vulkan-facing `wgpu` and `ash` APIs for frame pacing and GPU memory-budget reporting.
- Several platform-specific code paths are currently Windows-gated.
- Game data is represented as filesystem paths; Android's Storage Access Framework supplies document URIs and mediated access rather than ordinary unrestricted filesystem paths.

## Target architecture

### Android shell

A small Kotlin Android application owns platform integrations:

- Native Android activity and lifecycle.
- `ACTION_OPEN_DOCUMENT_TREE` game-folder selection.
- Persistable URI permissions.
- Installation validation and a clear diagnostic report.
- Gamepad discovery, Android key events and vibration/rumble routing.
- Android settings, permissions and app-specific writable storage.

### Native engine

The Rust/Bevy runtime is built for `aarch64-linux-android`. The Android shell passes a durable installation reference to native code. The engine must use an Android-compatible virtual filesystem adapter; it must not assume that a SAF URI is a normal POSIX path.

The engine's existing filesystem abstraction and archive readers should be reused where possible. If the current abstraction cannot read SAF-backed content efficiently, add a narrowly scoped provider/cache layer. Any cache must live in app-private storage and must never modify the selected game installation.

### Rendering and audio

Use Bevy's supported Android window/activity integration and Android Vulkan backend where the device supports the required extensions. Do not assume that desktop Vulkan feature requirements are available on every Android GPU. Device capability checks and a graceful unsupported-device report are required.

Audio must be tested against Android's audio backend and lifecycle transitions. Pause/resume, focus loss and headset changes need explicit handling.

### Input

Map Android touch controls to the existing game actions. Physical controllers should use Android's standard gamepad key/axis events, with configurable mappings and dead zones. Test reconnects, controller disconnects and rumble where supported.

## Milestones and acceptance criteria

### M0 — Android build skeleton
- Android project structure and reproducible CI setup.
- ARM64 Rust target and NDK toolchain configured.
- CI reports a successful debug APK build.
- No claim of engine integration or playability.

### M1 — Engine compiles for Android
- Integrate the upstream engine source without changing clean-room boundaries.
- Resolve all Android-incompatible dependencies and platform-specific code.
- Build the native library for ARM64.
- Keep desktop builds working.

### M2 — Installation access
- Select a game folder through SAF.
- Persist permission across app restarts.
- Validate required base-game masters and archive files.
- Read game data without writing to the selected folder.
- Handle revoked permissions and incomplete installations.

### M3 — Engine startup
- Start Bevy on Android and render a diagnostic scene.
- Load schemas and game data through the Android VFS.
- Handle pause/resume and activity recreation without corrupting state.

### M4 — Playable slice
- Reach the engine's main menu or a documented in-world test scene.
- Touch controls and a physical controller both work.
- Audio and controller disconnect/reconnect are handled.
- Record frame time, memory use and device compatibility on a physical device.

### M5 — APK release
- Signed release APK generated from protected GitHub secrets.
- Release notes state known limitations and supported devices.
- No game assets or proprietary content included.

## Immediate blockers

1. The upstream source tree must be integrated into this repository. It is not copied here yet.
2. The exact Bevy Android activity integration and Gradle packaging must be pinned to the upstream Bevy version.
3. Desktop-only Vulkan and window assumptions need a source-level audit.
4. The engine's filesystem and archive-loading paths need a SAF-compatible strategy.
5. Runtime viability and performance on the Pixel 9a must be measured, not assumed.

## Non-negotiable rules

- Never commit game data, extracted assets, saves, private parity goldens or proprietary game executables.
- Never modify the user's game installation.
- Preserve upstream GPL-3.0-or-later notices and clean-room requirements.
- Do not weaken or rewrite upstream parity tests to make Android changes pass.
- Distinguish build success, engine startup, game-data loading and actual gameplay; they are separate milestones.

## Android staging update

The launcher now has a cancellable, progress-reporting import path that stages the selected game Data directory into app-private storage. This resolves the URI-versus-filesystem-path mismatch for a future path-based VFS, but the staged directory is not yet consumed by the engine. The native engine remains blocked on a maintained writable engine fork and the Android GameActivity/cdylib refactor.
