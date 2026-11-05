# Android game-data staging

## Why the app stages the Data directory

The upstream `fnv-vfs` currently opens a normal filesystem directory and uses filesystem enumeration and file reads. Android's Storage Access Framework returns `content://` document URIs, not POSIX paths. Treating a URI as a path, or guessing a `/proc/self/fd` path, would be incorrect.

The launcher therefore has a deliberate staging boundary:

1. The player selects the original game root or its `Data` directory through SAF.
2. The app retains read permission only.
3. The importer enumerates the tree and estimates its size when the provider exposes lengths.
4. It checks available app-private storage with a 512 MiB safety margin.
5. It copies the tree into `filesDir/game-install.staging/Data`, reporting progress and checking cancellation between chunks.
6. It verifies all eight non-empty files required by `fnv_install::REQUIRED_DATA_FILES`.
7. It swaps the completed staging directory into `filesDir/game-install`, preserving the previous completed import if the swap fails. The final layout is `filesDir/game-install/Data`.

The source installation is never written to. Partial imports are discarded. The copy is app-private and is not exposed as shared storage.

## Runtime contract for the native engine

The imported directory is the synthetic game-install root at `filesDir/game-install`; its `Data` child contains `FalloutNV.esm`, plugin files, BSA archives and loose asset folders. Pass the install root as NVForever's `--data` argument. This is important: `fnv_install::GameInstall::open` treats a directly selected Data directory's parent as the game root, so placing the imported Data root directly under `filesDir` would make `filesDir/nvforever` appear to overlap the game installation and the engine would refuse to start.

The engine also needs its schema directories and script-function metadata. Those must be packaged from the clean-room upstream source and passed explicitly with `--esm-schema`, `--nif-schema`, and `NVF_PLAY_SCRIPT_FUNCTIONS` (or loaded from a known app-private path). Do not rely on Cargo checkout paths at runtime.

## Known limitations

- The importer currently copies the complete Data tree, which can require several gigabytes and time. A future VFS provider abstraction can eliminate this copy, but must support case-insensitive lookup, archive precedence, loose-file precedence, streaming reads, and concurrent access before replacing this path.
- SAF providers may not report file sizes. In that case the preflight cannot guarantee sufficient free space; the import can still fail safely and the previous completed import remains.
- This staging code does not start the engine. Native Bevy/GameActivity packaging and the Rust VFS/runtime bridge remain separate gates.
