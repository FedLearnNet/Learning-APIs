#!/usr/bin/env bash
# Healthcheck of the datamodeler service: healthy once it lists the root of every schema of datamodeler-imports
heads=$(exec 3<>/dev/tcp/localhost/8086 && printf 'GET /schemas/head/ HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && cat <&3) || exit 1
for file in /workspace/datamodeler-api/src/main/resources/datamodeler-imports/schemas/*.json; do
  grep -q "$(grep -m1 -o '"id": *"[0-9a-f-]*"' "${file}" | grep -o '[0-9a-f-]\{36\}')" <<< "${heads}" || exit 1
done
