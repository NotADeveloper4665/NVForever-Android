# Android port plan

## Current progress

- **M0 — Android shell:** launcher, SAF selection, controller diagnostics, importer, and initial debug APK packaging are implemented.
- **Engine source:** maintained in `NotADeveloper4665/NVForever-Android-Engine`, included as the `upstream/NVForever` submodule.
- **M1 — Android native build:** shared Rust library target, Bevy GameActivity entry point, ARM64 cargo-ndk workflow, and app-side native packaging are wired. The current ARM64 cross-build is being validated in CI.
- **M2 — Game-data staging:** persistent read-only SAF access and cancellable app-private Data import are implemented. The imported path is supplied to the engine through `NVFOREVER_DATA`.
- **M3 — Engine startup:** separate GameActivity and schema staging are wired in source; physical-device startup and data loading remain unverified.
- **M4 — Playable slice:** not yet accepted. Touch controls, full controller mapping/rumble, audio focus, and a stable in-world gameplay test remain.
- **M5 — Release:** not started.

A green shell build must not be described as a native engine build or playable APK.

## Target architecture

### Android shell

Kotlin owns the launcher, SAF folder picker, persistent read-only URI permissions, import worker, controller discovery, Android lifecycle, and app-private writable paths.

### Native engine

The Rust/Bevy runtime is built as an ARM64 shared library using Bevy's GameActivity backend. The Android activity sets the app-private data, home, and schema paths before Bevy starts. The original selected game installation is never modified.

### Rendering and audio

Use Bevy's Android Vulkan/WGPU path and validate on physical devices. MSAA is initially disabled on Android; GPU memory and rendering settings must be tuned after device testing. Audio sinks pause/resume with activity lifecycle events; audio focus and device routing still require validation.

### Input

The existing engine controls are keyboard/mouse and gamepad-oriented. Add a touch overlay mapped to existing actions, then test physical controllers, dead zones, reconnects, and rumble.

## Acceptance criteria

1. ARM64 native library builds reproducibly from the pinned engine submodule.
2. Gradle packages the native library and GameActivity into an installable APK.
3. Physical-device startup creates the Bevy Vulkan surface without crashing.
4. The imported Data directory and bundled schemas load successfully.
5. The engine reaches its main menu or a documented in-world test scene.
6. Touch and physical controller input can move, look, interact, and navigate menus.
7. Pause/resume, audio, saves, logs, and app-private paths remain correct.
8. A sustained playtest records frame time, memory, thermal behavior, and known limitations.

## Non-negotiable rules

- Never commit game data, extracted assets, saves, private parity goldens, or proprietary game executables.
- Never modify the user's game installation.
- Preserve GPL-3.0-or-later notices and clean-room requirements.
- Do not weaken or rewrite upstream parity tests to make Android changes pass.
- Distinguish build success, engine startup, data loading, and actual gameplay; they are separate milestones.
