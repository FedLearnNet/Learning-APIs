# FL-Net Learning APIs

The Java backend services of FL-Net, the federated learning platform also behind PosyMed.
This is a multi-module Maven project built with [Quarkus](https://quarkus.io/).

| Module                | Role                                                                                     | Dev port |
|-----------------------|------------------------------------------------------------------------------------------|:--------:|
| `core-learning-api`   | Shared domain logic, DTOs and helpers used by the other modules                          | –        |
| `global-learning-api` | Global server: projects, queries, workflows, federated runs, AI data-analysis agent      | 8080     |
| `local-learning-api`  | Local (hospital) server: data import, patient store, local query execution and tool runs | 8081     |
| `datamodeler-api`     | Data models, schemas and ontologies                                                      | 8086     |

## Documentation

All documentation can be found on the FL-Net documentation site:
- [FL-Net documentation](https://federated-learning.net/documentation/)

## Quick start

Requirements: **Java 25** and **Docker** (Quarkus Dev Services start the databases and orch-api from each
module's `compose-devservices.yml`). The Maven wrapper is included.

```bash
./mvnw clean install                # build all modules and run the tests

cd local-learning-api
cp .env.example .env                # fill in the secrets below
cp orch_secrets.env.example orch_secrets.env
./mvnw compile quarkus:dev          # live reload, Dev UI at /q/dev/
```

Second local node for federated runs (port 8082, own DB and containers): `local-learning-api/node2/start.sh`,
overrides in `node2/node2.env`.

## Secrets

Each module reads its secrets from environment variables or a module-local `.env` file (never commit it —
use the `.env.example` templates):
- For more detail information which secrets are relevant and how to use them vist the documentation.

## App-based connector transformations

Connector imports reuse one container per app transformer and send batches of complete patients.
The default limits are 100 patients and 1 MiB for the complete UTF-8 JSON message, including its
hyperparameters. Configure them on the local learning API:

```properties
flnet.connector.app-transform-batch-patients=100
flnet.connector.app-transform-max-message-bytes=1048576
```

Every app stage rechecks the byte budget after earlier transformations. A patient is never split;
if one patient's message exceeds the limit, the import fails with an explicit size error before
sending it. The configured WebSocket frame and message limits also bound the payload.

Batched tools must preserve the source patient-id column in their output. Standard Python Tool API
transformers preserve it; patient-mode tools group each batch by `patient_id_column` and transform
each patient separately. Set the patient limit to `1` for legacy tools that return only rewritten
columns. Built-in functions and database load transactions continue to run separately per patient.
Rebuild tool images with the updated Python Tool API to pick up immediate worker wakeups and queued
dispatch of back-to-back batches.

## Security

- Every REST endpoint needs a logged-in user by default, so you don't have to add `@Authenticated`.
  Use `@PermitAll` for public endpoints and `@RolesAllowed` for stricter ones. WebSockets still need `@Authenticated`.
- Tools authenticate with a per-run API key (`X-API-Key`) that only works for their own run.
- The frontends log in through `/bff/login`: the backend handles Keycloak and keeps the tokens in a session cookie.
- Swagger UI is only available in dev mode.
- Config per service: `security.properties` (OIDC/security), `openapi.properties` (OpenAPI/Swagger), `flnet.properties` (flnet.*), the rest in `application.properties`.

## Datasets

This repository contains public datasets:

1. [MIMIC-IV 2.2 demo](https://physionet.org/content/mimic-iv-demo/2.2/)
2. [Diabetes 130-US hospitals (1999–2008)](https://archive.ics.uci.edu/dataset/296/diabetes+130-us+hospitals+for+years+1999-2008)


## License

[Apache License 2.0](LICENSE) © Institute for Computational Systems Biomedicine and contributors.
