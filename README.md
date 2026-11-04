# NVForever Android

An Android port of [NVForever](https://github.com/RedZeroGotcha/NVForever-Android), the clean-room Rust reimplementation of Fallout: New Vegas.

**Status: Android port in progress — no installable APK is available yet.** This repository currently establishes the Android port workstream. The upstream game engine has not yet been integrated into this repository or validated on Android.

## Goals

- Build an ARM64 Android application for modern Android devices.
- Let users select their own Fallout: New Vegas installation using Android's Storage Access Framework (SAF).
- Validate the selected game data without modifying the original installation.
- Run the NVForever engine on Android with Vulkan rendering where supported.
- Support Android touch input and physical controllers.
- Produce debug and release APKs through GitHub Actions.

## Legal and content policy

This project does not distribute Fallout: New Vegas, its assets, executable, DLC, or other proprietary content. Users must provide game data from a copy they are entitled to use. The selected game directory is read-only. NVForever is an independent clean-room project and is not affiliated with Bethesda, ZeniMax, Microsoft, or Obsidian.

The upstream engine is GPL-3.0-or-later. See [LICENSE](LICENSE) once the upstream license and required notices have been incorporated into this port. Do not submit game files, extracted assets, proprietary source, or copyrighted game data.

## Current implementation status

| Component | Status |
|---|---|
| Android application project | Not yet integrated |
| Android SAF game-folder picker | Not yet implemented |
| Native Rust/Bevy engine integration | Not yet implemented |
| Android ARM64 build | Not yet verified |
| Vulkan/device compatibility | Not yet verified |
| Touch/controller mapping | Not yet implemented |
| APK CI/release pipeline | Not yet configured |

## Development plan

1. Establish Android project and CI build skeleton.
2. Port the NVForever Bevy runtime and resolve Android-only dependencies.
3. Implement SAF-backed game installation selection and read-only asset access.
4. Integrate Android lifecycle, audio, Vulkan and controller input.
5. Build and test on a physical ARM64 device, including the Pixel 9a.
6. Publish APK artifacts only after successful builds and runtime smoke tests.

See [docs/android-port-plan.md](docs/android-port-plan.md) for architecture, known blockers and acceptance criteria.

## Upstream

- Engine: https://github.com/RedZeroGotcha/NVForever-Android
- Project site: https://nv-forever.com/

This repository is an Android port workstream, not a claim that the desktop engine already runs on Android.
