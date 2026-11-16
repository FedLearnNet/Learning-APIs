# The ETL agent evaluation in one container with its own Docker daemon, like FL-Net-CLI/dind: the orchestrator starts
# the code runner and build pipeline next to the tests, whatever the Docker of the server allows (see README.md).
FROM docker:29.9.0-dind-alpine3.24

ENV DOCKER_TLS_CERTDIR=""

COPY --chmod=755 entrypoint.sh /opt/eval-dind/entrypoint.sh
COPY docker-compose.yml /opt/eval-dind/docker-compose.yml

ENTRYPOINT ["/opt/eval-dind/entrypoint.sh"]
