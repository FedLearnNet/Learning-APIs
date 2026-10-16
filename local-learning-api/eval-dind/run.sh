#!/usr/bin/env bash
# Builds eval-dind and runs the evaluations in it, on the checkout this script belongs to (see README.md):
#   local-learning-api/eval-dind/run.sh
#   EVAL_TESTS=gdc EVAL_RUNS=1 local-learning-api/eval-dind/run.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(cd "$DIR/../.." && pwd)"

docker build -t eval-dind "$DIR"

env_args=()
for name in EVAL_TESTS EVAL_MODELS EVAL_MODES EVAL_RUNS EVAL_RUN_NUMBERS EVAL_DATASETS EVAL_GDC_STUDIES EVAL_REPLACE \
            ORCH_API_IMAGE GLOBAL_API_URL GLOBAL_SCHEMA_API_URL; do
  if [ -n "${!name:-}" ]; then
    env_args+=(-e "$name")
  fi
done

# Privileged for the inner daemon; userns=host, as a Docker with user namespaces rejects privileged containers otherwise.
# No published port: the evaluation only connects out (LLM, global dev server, ghcr).
docker run --rm --name eval-dind \
  --privileged --userns=host --cgroupns host -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
  -v "$WORKSPACE:/workspace" \
  -v eval-dind-maven:/cache/m2 \
  -v eval-dind-docker:/var/lib/docker \
  ${env_args[@]+"${env_args[@]}"} \
  eval-dind
