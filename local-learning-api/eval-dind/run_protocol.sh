#!/usr/bin/env bash
# Runs the evaluation protocol of the AcETL paper in eval-dind (see README.md), then writes the summary and the
# package for the expert review. Started again with the same name, it continues where it stopped.
#
# Datasets: US-130 Sites 2-5 (cohort import and file harmonization), the MIMIC-IV demo (cohort import), GDC-SM (78 fields)
# and Synthea-OMOP. Settings, varied one at a time against the reference (full AcETL, primary model, real data):
#   configuration  real-matcher-only, real-no-review, real-no-writer
#   data exposure  synthetic, real-privacy (sanitized profiles)
#   model          every further model, real data
# Each setting runs RUNS times; results go to results/eval/<model>/<mode>/run-<name>-<n>.
#
#   local-learning-api/eval-dind/run_protocol.sh
#   NAME=paper RUNS=3 PRIMARY_MODEL=gpt-oss:20b MODELS=qwen3.8:27b local-learning-api/eval-dind/run_protocol.sh
#   STEPS=export NAME=paper local-learning-api/eval-dind/run_protocol.sh   # only the summary and the expert package
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$DIR/.." && pwd)"
NAME="${NAME:-protocol}"
RUNS="${RUNS:-3}"
PRIMARY_MODEL="${PRIMARY_MODEL:-gpt-oss:20b}"
MODELS="${MODELS:-nemotron-3.5-lightning:30b,qwen3.8:27b,gemma4:e4b}"
TESTS="${TESTS:-us130,mimic,gdc,synthea}"
STEPS="${STEPS:-runs,export}"
status=0

RUN_NUMBERS="$(seq -f "$NAME-%g" 1 "$RUNS" | paste -sd, -)"

step() {
  echo "▶ $1"
  shift
  env EVAL_TESTS="$TESTS" EVAL_RUN_NUMBERS="$RUN_NUMBERS" "$@" "$DIR/run.sh" || status=1
}

if [[ ",$STEPS," == *",runs,"* ]]; then
  [ -d "$MODULE/src/test/resources/eval/gdc/source-tables" ] || "$MODULE/download-gdc-sm.sh" || status=1
  [ -d "$MODULE/src/test/resources/eval/synthea-omop/source-tables" ] || "$MODULE/download-synthea-omop.sh" || status=1
  step "Primary model: reference, configurations and data exposure" EVAL_MODELS="$PRIMARY_MODEL" \
    EVAL_MODES="real,real-matcher-only,real-no-review,real-no-writer,synthetic,real-privacy"
  step "Further models: reference setting" EVAL_MODELS="$MODELS" EVAL_MODES=real
fi

if [[ ",$STEPS," == *",export,"* ]]; then
  python3 "$MODULE/eval-summary.py" "$MODULE/results/eval" --runs "$NAME" \
    --markdown "$MODULE/results/eval/summary-$NAME.md" >/dev/null || status=1
  python3 "$MODULE/eval-export.py" "$MODULE/results/eval" --runs "$NAME" \
    --out "$MODULE/results/expert-review-$NAME" || status=1
  echo "Summary: $MODULE/results/eval/summary-$NAME.md"
  echo "Expert package: $MODULE/results/expert-review-$NAME (give the folder for-expert to the expert)"
fi
exit "$status"
