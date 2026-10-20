#!/usr/bin/env bash
# Runs every evaluation of the ETL agent in eval-dind, one after the other (see README.md): US-130 real and synthetic,
# GDC-SM on its 78 and on all 736 fields, and Synthea-OMOP; the data of the benchmarks is downloaded first if missing.
# Results go to local-learning-api/results/eval/<model>/<mode>/run-<name>-<n>, the mean ± SD of all of them to
# results/eval/summary-<name>.md. Started again with the same name, it continues where it stopped.
#   local-learning-api/eval-dind/run_all.sh
#   NAME=v3 RUNS=3 FULL_RUNS=1 SYNTHEA_RUNS=1 local-learning-api/eval-dind/run_all.sh
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$DIR/.." && pwd)"
NAME="${NAME:-$(date +%Y-%m-%d)}"
status=0

# Run numbers <name>-1..n, e.g. v3-1,v3-2,v3-3
runs() {
  seq -f "$1-%g" 1 "$2" | paste -sd, -
}

step() {
  echo "▶ $1"
  shift
  env "$@" "$DIR/run.sh" || status=1
}

[ -d "$MODULE/src/test/resources/eval/gdc/source-tables" ] || "$MODULE/download-gdc-sm.sh" || status=1
[ -d "$MODULE/src/test/resources/eval/synthea-omop/source-tables" ] || "$MODULE/download-synthea-omop.sh" || status=1

step "US-130" EVAL_TESTS=us130 EVAL_MODES=real,synthetic EVAL_RUN_NUMBERS="$(runs "$NAME" "${RUNS:-3}")"
step "GDC-SM, 78 fields" EVAL_TESTS=gdc EVAL_MODES=real EVAL_RUN_NUMBERS="$(runs "$NAME" "${RUNS:-3}")"
step "GDC-SM, 736 fields" EVAL_TESTS=gdc EVAL_MODES=real EVAL_GDC_SCHEMA=full \
  EVAL_RUN_NUMBERS="$(runs "$NAME-full" "${FULL_RUNS:-1}")"
step "Synthea-OMOP" EVAL_TESTS=synthea EVAL_MODES=real EVAL_RUN_NUMBERS="$(runs "$NAME" "${SYNTHEA_RUNS:-1}")"

python3 "$MODULE/eval-summary.py" "$MODULE/results/eval" --markdown "$MODULE/results/eval/summary-$NAME.md" || status=1
echo "Summary: $MODULE/results/eval/summary-$NAME.md"
exit "$status"
