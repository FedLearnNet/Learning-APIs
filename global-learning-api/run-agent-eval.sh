#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="bio.cosy.feddb.core.agent.ModelWorkflowAgentEvaluationTest"
LOG_DIR="logs"
TIMESTAMP="$(date +"%Y-%m-%d_%H-%M-%S")"
LOG_FILE="${LOG_DIR}/posymed-agent-eval_${TIMESTAMP}.log"

mkdir -p "${LOG_DIR}"

export FLNET_INGESTOR_ENABLE_START_UP=true

echo "Starting test: ${TEST_CLASS}"
echo "Log file: ${LOG_FILE}"
echo "FLNET_INGESTOR_ENABLE_START_UP=${FLNET_INGESTOR_ENABLE_START_UP}"
echo

mvn -Dtest="${TEST_CLASS}" test 2>&1 | tee "${LOG_FILE}"

EXIT_CODE=${PIPESTATUS[0]}

echo
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "Test finished successfully."
else
  echo "Test failed with exit code ${EXIT_CODE}."
fi
echo "Log written to: ${LOG_FILE}"

exit "${EXIT_CODE}"