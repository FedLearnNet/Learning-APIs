#!/usr/bin/env bash
# Runs run_protocol.sh unattended in the background: up to ATTEMPTS times, each one continuing where the last stopped,
# with a pause in between, e.g. when the LLM server was down. It survives closing the terminal or logging out; on macOS,
# caffeinate keeps the machine awake while it runs. The variables of run_protocol.sh (NAME, RUNS, MODELS, ...) are
# passed on.
#   NAME=paper local-learning-api/eval-dind/run_protocol_unattended.sh
#   tail -f local-learning-api/logs/protocol-paper.log
#   kill $(cat local-learning-api/logs/protocol-paper.pid); docker stop eval-dind   # stop it
# A failed attempt can also be a test whose assertion failed (e.g. a wrong value); its results are kept all the same.
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE="$(cd "$DIR/.." && pwd)"
NAME="${NAME:-protocol}"
ATTEMPTS="${ATTEMPTS:-3}"
PAUSE="${PAUSE:-600}"
LOG="$MODULE/logs/protocol-$NAME.log"
PID_FILE="$MODULE/logs/protocol-$NAME.pid"

if [ "${1:-}" != "--attached" ]; then
  mkdir -p "$MODULE/logs"
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Already running (pid $(cat "$PID_FILE")), log: $LOG" >&2
    exit 1
  fi
  NAME="$NAME" nohup "$0" --attached >> "$LOG" 2>&1 < /dev/null &
  echo $! > "$PID_FILE"
  echo "Started in the background (pid $!), log: $LOG"
  exit 0
fi

# Keeps a Mac awake as long as this script runs
command -v caffeinate >/dev/null && caffeinate -i -w $$ &

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "=== Attempt $attempt of $ATTEMPTS: $(date) ==="
  if NAME="$NAME" "$DIR/run_protocol.sh"; then
    echo "=== Done: $(date) ==="
    rm -f "$PID_FILE"
    exit 0
  fi
  if [ "$attempt" -lt "$ATTEMPTS" ]; then
    echo "=== Attempt $attempt failed, next one in ${PAUSE}s ==="
    sleep "$PAUSE"
  fi
done
echo "=== Not complete after $ATTEMPTS attempts: $(date); start it again with the same NAME to continue ==="
rm -f "$PID_FILE"
exit 1
