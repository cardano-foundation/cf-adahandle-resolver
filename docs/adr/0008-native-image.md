# 8. A native image built with Oracle GraalVM, checked weekly, published with releases

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
- The weekly build and publishing with each release follow
  [cf-java-nfrs NFR-02](https://github.com/cardano-foundation/cf-java-nfrs/blob/main/java-microservice-nfrs/NFR-02-native-image.md). Where the
  resolver departs from it:
  - the weekly build runs smoke tests (API on H2, Flyway on PostgreSQL, 3 minutes of mainnet sync), not the devnet
    integration suite against the native container (N2);
  - the image is published in the same repository as the JVM image, tagged `<version>-native`, not as a separate
    `<service>-native` repository (N8).
- The JVM image stays the default; the native image is experimental.

## Consequences

- It starts in under 2 s (the JVM image: about 9 s) and used about 1.1 GB of memory throughout a mainnet sync with
  `-Xmx4g` (2026-11-05). Its sync speed relative to the JVM image is still being measured.
- JVM flags go on the command line (`-Xmx2g`), not in `JAVA_TOOL_OPTIONS`.
- A breakage shows up in the weekly build at the latest, which opens or updates an issue labelled `native-image`.

## References

- cf-java-nfrs NFR-02; #63, #82, #95 (until #95, the weekly build was published as `:native-weekly`); `Dockerfile.native`, `.github/workflows/native-image.yaml`, `config/NativeImageConfig.java`.
