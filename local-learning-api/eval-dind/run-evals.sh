#!/usr/bin/env bash
# Runs inside eval-dind (docker-compose.yml): the US-130 evaluations and the GDC benchmark, one after the other.
# Results go to local-learning-api/results/eval of the mounted checkout.
set -uo pipefail

cd /workspace
status=0

if [[ ",${EVAL_TESTS}," == *",us130,"* ]]; then
  echo "▷ US-130: transformer writer and harmonization"
  local-learning-api/run-us130-eval.sh || status=1
fi

if [[ ",${EVAL_TESTS}," == *",gdc,"* ]]; then
  if [ ! -d local-learning-api/src/test/resources/eval/gdc/source-tables ]; then
    echo "▷ GDC skipped: run local-learning-api/download-gdc-sm.sh on the checkout first" >&2
    exit 1
  fi
  IFS=',' read -r -a modes <<< "${EVAL_MODES}"
  IFS=',' read -r -a runs <<< "${EVAL_RUN_NUMBERS:-$(seq -s, 1 "${EVAL_RUNS}")}"
  for mode in "${modes[@]}"; do
    for run in "${runs[@]}"; do
      echo "▷ GDC benchmark | ${mode} | run ${run}"
      ./mvnw -pl local-learning-api -am test \
        -Dtest=GdcBenchmarkEvaluationTest \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -Deval.run="${run}" \
        -Deval.replace="${EVAL_REPLACE}" \
        -Deval.datasets="${EVAL_GDC_STUDIES:-}" \
        -Deval.gdc.schema="${EVAL_GDC_SCHEMA:-}" \
        -Dflnet.connector.agent.synthetic-data.enabled="$([[ "${mode}" == synthetic* ]] && echo true || echo false)" \
        -Dflnet.connector.agent.use-statistic-privacy="$([[ "${mode}" == *privacy ]] && echo true || echo false)" \
        ${EVAL_MODELS:+-Dquarkus.langchain4j.openai.chat-model.model-name="${EVAL_MODELS%%,*}"} \
        || status=1
    done
  done
fi

exit "$status"
