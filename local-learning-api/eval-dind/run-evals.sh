#!/usr/bin/env bash
# Runs inside eval-dind (docker-compose.yml): the US-130 evaluations and the GDC and Synthea-OMOP benchmarks, one
# after the other. Results go to local-learning-api/results/eval of the mounted checkout.
set -uo pipefail

cd /workspace
status=0

# A schema matching benchmark for every mode and run: name, test class, data folder, download script, studies, extra args
benchmark() {
  local name="$1" test="$2" data="$3" download="$4" studies="$5"
  shift 5
  if [ ! -d "local-learning-api/src/test/resources/eval/${data}/source-tables" ]; then
    echo "▷ ${name} skipped: run local-learning-api/${download} on the checkout first" >&2
    status=1
    return
  fi
  IFS=',' read -r -a modes <<< "${EVAL_MODES}"
  IFS=',' read -r -a runs <<< "${EVAL_RUN_NUMBERS:-$(seq -s, 1 "${EVAL_RUNS}")}"
  IFS=',' read -r -a models <<< "${EVAL_MODELS:-}"
  [ "${#models[@]}" -eq 0 ] && models=("")
  for model in "${models[@]}"; do
  for mode in "${modes[@]}"; do
    for run in "${runs[@]}"; do
      echo "▷ ${name} benchmark | ${model:-default model} | ${mode} | run ${run}"
      ./mvnw -pl local-learning-api -am test \
        -Dtest="${test}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -Deval.run="${run}" \
        -Deval.replace="${EVAL_REPLACE}" \
        -Deval.datasets="${studies}" \
        -Dflnet.connector.agent.synthetic-data.enabled="$([[ "${mode}" == synthetic* ]] && echo true || echo false)" \
        -Dflnet.connector.agent.use-statistic-privacy="$([[ "${mode}" == *privacy* ]] && echo true || echo false)" \
        -Deval.mode="${mode}" \
        ${model:+-Dquarkus.langchain4j.openai.chat-model.model-name="${model}"} \
        "$@" \
        || status=1
    done
  done
  done
}

if [[ ",${EVAL_TESTS}," == *",us130,"* ]]; then
  echo "▷ US-130: transformer writer and harmonization"
  local-learning-api/run-us130-eval.sh || status=1
fi

# The MIMIC-IV demo onto the US-130 schema, cohort import only, in its own run so it starts from the same catalog
if [[ ",${EVAL_TESTS}," == *",mimic,"* ]]; then
  echo "▷ MIMIC-IV demo: transformer writer"
  EVAL_DATASETS="MIMIC IV Demo" EVAL_TEST_CLASSES=TransformerWriterEvaluationTest local-learning-api/run-us130-eval.sh \
    || status=1
fi

if [[ ",${EVAL_TESTS}," == *",gdc,"* ]]; then
  benchmark GDC GdcBenchmarkEvaluationTest gdc download-gdc-sm.sh "${EVAL_GDC_STUDIES:-}" \
    -Deval.gdc.schema="${EVAL_GDC_SCHEMA:-}"
fi

if [[ ",${EVAL_TESTS}," == *",synthea,"* ]]; then
  benchmark Synthea-OMOP SyntheaOmopEvaluationTest synthea-omop download-synthea-omop.sh "${EVAL_SYNTHEA_TABLES:-}"
fi

exit "$status"
