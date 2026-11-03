# 8. A native image built with Oracle GraalVM, weekly from main

- Status: Accepted
- Date: 2026-11-03

## Context

A GraalVM native image starts in about a second and needs far less memory than the JVM. GraalVM Community Edition only
has the Serial collector, which made a mainnet sync much slower than the JVM. Oracle GraalVM adds G1 and `-O3`, under
the GraalVM Free Terms and Conditions (GFTC): free use, including production and paid hosted services, and free
redistribution, but not redistribution for a fee; Oracle's license and notices must be kept with it.

## Decision

- Build with Oracle GraalVM, pinned to one version (`Dockerfile.native`) so an upgrade, or a change of license terms,
  is a reviewed change. Options: G1 on Linux, `-O3`, `-march=compatibility` (so the image runs on older x86-64 CPUs and
  generic VM CPU models).
- The image ships Oracle's `LICENSE.txt` and `NOTICE.txt` in `/licenses/graalvm` and our MIT license in
  `/licenses/LICENSE`, with the OCI label `MIT AND LicenseRef-GraalVM-Free-Terms-and-Conditions`. CI fails if a license
  file is missing.
- Build weekly from `main` (Mondays 03:00 UTC), and by hand on any branch; only `main`'s build is published, as
  `cardanofoundation/adahandle-resolver:native-weekly`. It is not built on pull requests (about 25 minutes per build):
  run it by hand before merging a change that may need new reachability metadata.
- The JVM image stays the default; the native image is experimental.

## Consequences

- With G1, the native image syncs at roughly 80% of the JVM's speed with about half its memory.
- JVM flags go on the command line (`-Xmx2g`), not in `JAVA_TOOL_OPTIONS`.
- A breakage shows up in the weekly build at the latest, which opens or updates an issue labelled `native-image`.

## References

- #63, #82; `Dockerfile.native`, `.github/workflows/native-image.yaml`, `config/NativeImageConfig.java`.
