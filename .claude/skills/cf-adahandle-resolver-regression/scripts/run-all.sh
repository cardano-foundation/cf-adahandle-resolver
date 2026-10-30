#!/usr/bin/env bash
# Runs the regression suite against the instance deployed by the full-sync skill and writes a
# Markdown report to reports/. Exits non-zero if any check failed.
#
#   scripts/run-all.sh [--with-restart]
#
# --with-restart adds check-restart-rollback.sh, which RESTARTS the API container.

set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$DIR/lib.sh"

checks=(check-deployment check-api check-sync-health check-data-integrity golden check-oracle check-coverage)
[[ "${1:-}" == "--with-restart" ]] && checks+=(check-restart-rollback)

mkdir -p "$REG_DIR/reports"
report="$REG_DIR/reports/regression-$(date -u +%Y%m%dT%H%MZ).md"
results=()

{
  echo "# cf-adahandle-resolver regression report"
  echo
  echo "Host \`$REMOTE\`, branch \`$BRANCH\`, $(date -u '+%Y-%m-%d %H:%M UTC')"
  echo
  echo '```'
} > "$report"

for check in "${checks[@]}"; do
  start=$SECONDS
  case "$check" in
    golden) cmd=(bash "$SYNC_SKILL_DIR/scripts/run-golden-handles.sh") ;;
    check-restart-rollback) cmd=(bash "$DIR/$check.sh" --yes) ;;
    *) cmd=(bash "$DIR/$check.sh") ;;
  esac
  [[ "$check" == golden ]] && echo "[golden]"
  if "${cmd[@]}" < /dev/null 2>&1 | grep -v '^\[config\]' | tee -a "$report"; then
    results+=(PASS)
  else
    results+=(FAIL)
  fi
  echo "  ($(( SECONDS - start ))s)" | tee -a "$report"
done

{
  echo '```'
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  for i in "${!checks[@]}"; do echo "| ${checks[$i]} | ${results[$i]} |"; done
} >> "$report"

echo
printf '%-24s %s\n' CHECK RESULT
failed=0
for i in "${!checks[@]}"; do
  printf '%-24s %s\n' "${checks[$i]}" "${results[$i]}"
  [[ "${results[$i]}" == PASS ]] || failed=1
done
echo
echo "report: $report"
exit $failed
