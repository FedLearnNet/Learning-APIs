#!/usr/bin/env bash
# Runs the ETL agent and the harmonization evaluation on the US-130 sites.
# Stop `quarkus dev` first: the test profile shares and wipes the dev database.
# Extra arguments go to Maven, e.g. -Dflnet.connector.agent.synthetic-data.enabled=true
set -euo pipefail

TEST_CLASSES="TransformerWriterEvaluationTest,HarmonizationFileEvaluationTest"
DATASETS="${EVAL_DATASETS:-US 130 Site 2,US 130 Site 3,US 130 Site 4,US 130 Site 5}"
MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="${MODULE_DIR}/logs"
TIMESTAMP="$(date +"%Y-%m-%d_%H-%M-%S")"
LOG_FILE="${LOG_DIR}/us130-eval_${TIMESTAMP}.log"

if [ -z "${QUARKUS_LANGCHAIN4J_OPENAI_API_KEY:-}" ]; then
  echo "Set QUARKUS_LANGCHAIN4J_OPENAI_API_KEY to the key of the LLM first." >&2
  exit 1
fi

mkdir -p "${LOG_DIR}"

echo "Starting tests: ${TEST_CLASSES}"
echo "Datasets: ${DATASETS}"
echo "Log file: ${LOG_FILE}"
echo "Results: ${MODULE_DIR}/results/eval"
echo

# From the parent, so the changed core-learning-api is built as well
cd "${MODULE_DIR}/.."
set +e
./mvnw -pl local-learning-api -am test \
  -Dtest="${TEST_CLASSES}" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Deval.datasets="${DATASETS}" \
  "$@" 2>&1 | tee "${LOG_FILE}"
EXIT_CODE=${PIPESTATUS[0]}
set -e

echo
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "Tests finished successfully."
else
  echo "Tests failed with exit code ${EXIT_CODE}."
fi
echo "Log written to: ${LOG_FILE}"

exit "${EXIT_CODE}"
