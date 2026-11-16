#!/usr/bin/env bash
# Second local node in dev mode, same code as the normal one.
# Overrides in node2.env, own build dir target-node2 (Maven profile node2).
set -euo pipefail
cd "$(dirname "$0")/.."

set -a
source node2/node2.env
set +a

exec ./mvnw quarkus:dev -Pnode2 -Ddebug=5006 "$@"
