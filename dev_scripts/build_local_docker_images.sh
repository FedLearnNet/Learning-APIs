#!/bin/sh
# Small helper script to build the docker images for all services to use in testing bundle.
# Comment out any block you don't need.
set -eu
cd "$(dirname "$0")/.."

# The testing bundle always pulls new images on startup but ignores this tag,
# so keep the tag if you wanna use it in the testing bundle
TAG="${1:-testingtag}"

# Build Quarkus applications
./mvnw -B -ntp package -DskipTests

# global-learning-api
docker build -t "global-learning-api:$TAG" \
  -f global-learning-api/src/main/docker/Dockerfile.jvm global-learning-api

# local-learning-api
docker build -t "local-learning-api:$TAG" \
  -f local-learning-api/src/main/docker/Dockerfile.jvm local-learning-api

# datamodeler-api
docker build -t "datamodeler-api:$TAG" \
  -f datamodeler-api/src/main/docker/Dockerfile.jvm datamodeler-api