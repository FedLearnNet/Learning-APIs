#!/usr/bin/env bash
# Runs inside eval-dind (docker-compose.yml): the datamodeler of the checkout in dev mode on localhost:8086, which
# imports the schemas of datamodeler-imports with their fixed ids into the datamodeler-db service.
set -euo pipefail

cd /workspace
# SapBERT is not part of the repository: the model of the datamodeler, else the one of the local learning API
sapbert=datamodeler-api/src/main/resources/sapbert
[ -f "${sapbert}/sapbert.onnx" ] || sapbert=local-learning-api/src/main/resources/sapbert
./mvnw -q -pl core-learning-api -am install -DskipTests
exec ./mvnw -pl datamodeler-api quarkus:dev -Dquarkus.console.enabled=false -Dquarkus.compose.devservices.enabled=false \
  -Dembedding.model.path-to-model="/workspace/${sapbert}/sapbert.onnx" \
  -Dembedding.model.path-to-tokenizer="/workspace/${sapbert}/tokenizer.json"
