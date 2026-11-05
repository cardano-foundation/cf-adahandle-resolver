# 13. Pre-releases X.Y.Z-preN before each final release

- Status: Accepted
- Date: 2026-11-05

## Context

Until now a release was either a final version or nothing. `:latest` on Docker Hub had followed snapshots
(`0.1.6-SNAPSHOT`), and there was no way to hand out a build for testing under a stable, released name before the
final.

## Decision

- A release can be preceded by pre-releases: `X.Y.Z-pre1`, `X.Y.Z-pre2`, …, then the final `X.Y.Z` without a suffix.
  Pre-releases are normal releases: a tag `vX.Y.Z-preN`, a GitHub release (marked as a pre-release), a changelog
  entry and Docker images.
- Each pre-release and the final after them gets its version from a `Release-As:` footer on a commit to `main`
  (`Release-As: 1.0.0-pre1`). release-please's own version bump is only used when no footer is given.
- Docker images:

  | Version | JVM image | Native image |
  |---|---|---|
  | `1.0.0-pre1` | `:1.0.0-pre1` | `:1.0.0-pre1-native` |
  | `1.0.0` | `:1.0.0` and `:latest` | `:1.0.0-native` |

  `:latest` moves only with a final release.
- Anything else is refused by the publish workflows: snapshots, other suffixes (`-beta.1`, `-rc1`), `-pre0`, or a tag
  that doesn't match `pom.xml`.

## Consequences

- Testers can run a pre-release by its tag; people who follow `:latest` never get one.
- Every pre-release and every final needs a `Release-As:` footer. Without one, release-please computes the next
  version from the commits, which isn't a `-preN` version.
- release-please's `prerelease` option also marks versions below 1.0.0 as pre-releases on GitHub; from 1.0.0 on, that
  doesn't matter.

## References

- #91; `.github/workflows/release.yaml`, `publish.yaml`, `native-image.yaml`.
