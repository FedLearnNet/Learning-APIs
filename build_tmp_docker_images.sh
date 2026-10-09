#!/usr/bin/env bash
# Small helper script to build the docker images for all services to use in testing.
# Comment out any block you don't need.
set -euo pipefail
cd "$(dirname "$0")"

TAG=tmptag

# Build Quarkus applications
bash mvnw -B -ntp package -DskipTests -Dquarkus.profile=staging

# global-learning-api
docker build -t "ghcr.io/fedlearnnet/learning-apis/global-learning-api:$TAG" \
  -f global-learning-api/src/main/docker/Dockerfile.jvm global-learning-api

# local-learning-api
docker build -t "ghcr.io/fedlearnnet/learning-apis/local-learning-api:$TAG" \
  -f local-learning-api/src/main/docker/Dockerfile.jvm local-learning-api

# datamodeler-api
docker build -t "ghcr.io/fedlearnnet/learning-apis/datamodeler-api:$TAG" \
  -f datamodeler-api/src/main/docker/Dockerfile.jvm datamodeler-api
