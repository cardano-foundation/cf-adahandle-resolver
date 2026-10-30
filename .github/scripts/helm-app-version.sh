#!/usr/bin/env bash
# Sets the Helm chart's appVersion (the default image tag) to the project version in pom.xml.
# Run on release-please's release PR, so a release ships a chart that deploys that release.
# SNAPSHOT versions (release-please's post-release snapshot PRs) are left alone: no image is
# published for them. Prints the version it set, or nothing if it changed nothing.
set -euo pipefail

CHART="${CHART:-deploy/adahandle-resolver/Chart.yaml}"
POM="${POM:-pom.xml}"

version=$(python3 -c '
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text)' "$POM")

case "$version" in
  *-SNAPSHOT) echo "skip: $version is a SNAPSHOT" >&2; exit 0 ;;
esac

current=$(sed -n 's/^appVersion: "\(.*\)"$/\1/p' "$CHART")
if [[ "$current" == "$version" ]]; then
  echo "skip: appVersion is already $version" >&2
  exit 0
fi

sed -i.bak "s/^appVersion: \".*\"$/appVersion: \"$version\"/" "$CHART" && rm -f "$CHART.bak"
echo "$version"
