# Android game-data staging

## Why the app stages the Data directory

The upstream `fnv-vfs` currently opens a normal filesystem directory and uses filesystem enumeration and file reads. Android's Storage Access Framework returns `content://` document URIs, not POSIX paths. Treating a URI as a path, or guessing a `/proc/self/fd` path, would be incorrect.

The launcher therefore has a deliberate staging boundary:

1. The player selects the original game root or its `Data` directory through SAF.
2. The app retains read permission only.
3. The importer enumerates the tree and estimates its size when the provider exposes lengths.
4. It checks available app-private storage with a 512 MiB safety margin.
5. It copies the tree into `filesDir/game-data.staging`, reporting progress and checking cancellation between chunks.
6. It verifies `FalloutNV.esm` and at least one root-level BSA.
7. It swaps the completed staging directory into `filesDir/game-data`, preserving the previous completed import if the swap fails.

The source installation is never written to. Partial imports are discarded. The copy is app-private and is not exposed as shared storage.

## Runtime contract for the native engine

The imported directory is the Data root (it contains `FalloutNV.esm`, plugin files, BSA archives and loose asset folders). The future native launcher must pass this exact path as NVForever's `--data` argument.

The engine also needs its schema directories and script-function metadata. Those must be packaged from the clean-room upstream source and passed explicitly with `--esm-schema`, `--nif-schema`, and `NVF_PLAY_SCRIPT_FUNCTIONS` (or loaded from a known app-private path). Do not rely on Cargo checkout paths at runtime.

## Known limitations

- The importer currently copies the complete Data tree, which can require several gigabytes and time. A future VFS provider abstraction can eliminate this copy, but must support case-insensitive lookup, archive precedence, loose-file precedence, streaming reads, and concurrent access before replacing this path.
- SAF providers may not report file sizes. In that case the preflight cannot guarantee sufficient free space; the import can still fail safely and the previous completed import remains.
- This staging code does not start the engine. Native Bevy/GameActivity packaging and the Rust VFS/runtime bridge remain separate gates.
