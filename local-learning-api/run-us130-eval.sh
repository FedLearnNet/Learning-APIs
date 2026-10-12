#!/usr/bin/env bash
# Runs the ETL agent and the harmonization evaluation on the US-130 sites, for every model, mode and run.
# Start it again after a crash: complete combinations are skipped, a stopped one continues with the datasets it has
# no results for yet, and a dataset the LLM failed on (e.g. its server was down) is evaluated again (see EvaluationRun).
# EVAL_REPLACE=true starts the selected combinations fresh.
# Stop `quarkus dev` first: the test profile shares and wipes the dev database.
#
#   EVAL_MODELS    comma separated models, default the one of application.properties
#   EVAL_MODES     real, synthetic, real-privacy, synthetic-privacy (comma separated), default all
#   EVAL_RUNS      runs per model and mode, default 3
#   EVAL_RUN_NUMBERS  only these runs (comma separated), e.g. 2 to restart run 2
#   EVAL_DATASETS  comma separated datasets, default the four US-130 sites
#   EVAL_JUDGE     model of the LLM judge, the same for all models, default gpt-oss:latest
# Extra arguments go to Maven.
set -euo pipefail

TEST_CLASSES="TransformerWriterEvaluationTest,HarmonizationFileEvaluationTest"
MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
MODELS="${EVAL_MODELS:-$(sed -n 's/^quarkus\.langchain4j\.openai\.chat-model\.model-name=//p' "${MODULE_DIR}/src/main/resources/application.properties")}"
MODES="${EVAL_MODES:-real,synthetic,real-privacy,synthetic-privacy}"
RUNS="${EVAL_RUN_NUMBERS:-$(seq -s, 1 "${EVAL_RUNS:-3}")}"
DATASETS="${EVAL_DATASETS:-US 130 Site 2,US 130 Site 3,US 130 Site 4,US 130 Site 5}"
JUDGE="${EVAL_JUDGE:-gpt-oss:latest}"
LOG_DIR="${MODULE_DIR}/logs"
TIMESTAMP="$(date +"%Y-%m-%d_%H-%M-%S")"
LOG_FILE="${LOG_DIR}/us130-eval_${TIMESTAMP}.log"
FAILED=()

mkdir -p "${LOG_DIR}"

echo "Tests: ${TEST_CLASSES}"
echo "Models: ${MODELS} | Modes: ${MODES} | Runs: ${RUNS} | Judge: ${JUDGE}"
echo "Datasets: ${DATASETS}"
echo "Log file: ${LOG_FILE}"
echo "Results: ${MODULE_DIR}/results/eval/<model>/<mode>/run-<n>"
echo

# Both tests marked the combination complete for the selected datasets
is_complete() {
  local test
  for test in transformer-writer harmonization-file; do
    [ -f "$1/${test}.done" ] && [ "$(cat "$1/${test}.done")" == "${DATASETS}" ] || return 1
  done
}

# From the parent, so the changed core-learning-api is built as well
cd "${MODULE_DIR}/.."
IFS=',' read -r -a MODEL_LIST <<< "${MODELS}"
IFS=',' read -r -a MODE_LIST <<< "${MODES}"
IFS=',' read -r -a RUN_LIST <<< "${RUNS}"
for MODEL in "${MODEL_LIST[@]}"; do
  for MODE in "${MODE_LIST[@]}"; do
    SYNTHETIC=false
    PRIVACY=false
    [[ "${MODE}" == synthetic* ]] && SYNTHETIC=true
    [[ "${MODE}" == *privacy ]] && PRIVACY=true
    for RUN in "${RUN_LIST[@]}"; do
      echo "=== ${MODEL} | ${MODE} | run ${RUN} ===" | tee -a "${LOG_FILE}"
      RESULT_DIR="${MODULE_DIR}/results/eval/${MODEL%:latest}/${MODE}/run-${RUN}"
      if [ "${EVAL_REPLACE:-false}" != "true" ] && is_complete "${RESULT_DIR}"; then
        echo "Complete, skipped: ${RESULT_DIR}" | tee -a "${LOG_FILE}"
        continue
      fi
      set +e
      ./mvnw -pl local-learning-api -am test \
        -Dtest="${TEST_CLASSES}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -Deval.datasets="${DATASETS}" \
        -Deval.run="${RUN}" \
        -Deval.replace="${EVAL_REPLACE:-false}" \
        -Deval.judge-model="${JUDGE}" \
        -Dquarkus.langchain4j.openai.chat-model.model-name="${MODEL}" \
        -Dflnet.connector.agent.synthetic-data.enabled="${SYNTHETIC}" \
        -Dflnet.connector.agent.use-statistic-privacy="${PRIVACY}" \
        "$@" 2>&1 | tee -a "${LOG_FILE}"
      EXIT_CODE=${PIPESTATUS[0]}
      set -e
      if [ "${EXIT_CODE}" -ne 0 ]; then
        FAILED+=("${MODEL} | ${MODE} | run ${RUN}")
      fi
    done
  done
done

echo
if [ "${#FAILED[@]}" -eq 0 ]; then
  echo "All combinations finished successfully."
else
  echo "Failed combinations (start the script again to continue them):"
  printf '  %s\n' "${FAILED[@]}"
fi
echo "Log written to: ${LOG_FILE}"

[ "${#FAILED[@]}" -eq 0 ]
