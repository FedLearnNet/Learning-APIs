#!/bin/sh
# Starts the inner Docker daemon, then the database, the orchestrator and the evaluation; ends with the result of the
# evaluation, so the container stops when it is done.
set -eu

unset DOCKER_HOST

# An empty /workspace means the Docker of the host cannot see the checkout (e.g. Snap Docker outside /home, or a remote DOCKER_HOST)
if [ ! -f /workspace/local-learning-api/eval-dind/run-evals.sh ]; then
  echo "The checkout is not mounted at /workspace, run eval-dind/run.sh on the machine of the Docker daemon" >&2
  exit 1
fi

echo "▷ Starting Docker daemon"
# Inner networks off 172.17.0.0/16, which the docker0 of a Linux host uses (see FL-Net-CLI/dind)
dockerd-entrypoint.sh dockerd --bip 172.31.0.1/24 --default-address-pool base=172.30.0.0/16,size=24 \
  >/var/log/dockerd.log 2>&1 &
dockerd_pid=$!

timeout=60
until docker info >/dev/null 2>&1; do
  timeout=$((timeout - 1))
  if [ "$timeout" -le 0 ] || ! kill -0 "$dockerd_pid" 2>/dev/null; then
    echo "Docker daemon failed to start:" >&2
    tail -n 50 /var/log/dockerd.log >&2 || true
    exit 1
  fi
  sleep 1
done

cd /opt/eval-dind
# Containers and networks of a run that was killed stay in the Docker storage volume
docker compose down --volumes --remove-orphans >/dev/null 2>&1 || true
docker network rm eval-network >/dev/null 2>&1 || true
status=0
docker compose up --abort-on-container-exit --exit-code-from eval || status=$?
docker compose down --volumes --remove-orphans >/dev/null 2>&1 || true
kill "$dockerd_pid" 2>/dev/null || true
exit "$status"
