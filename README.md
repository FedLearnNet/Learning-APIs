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

## Secrets

Each module reads its secrets from environment variables or a module-local `.env` file (never commit it —
use the `.env.example` templates):
- For more detail information which secrets are relevant and how to use them vist the documentation.


## Datasets

This repository contains public datasets:

1. [MIMIC-IV 2.2 demo](https://physionet.org/content/mimic-iv-demo/2.2/)
2. [Diabetes 130-US hospitals (1999–2008)](https://archive.ics.uci.edu/dataset/296/diabetes+130-us+hospitals+for+years+1999-2008)


## License

[Apache License 2.0](LICENSE) © Institute for Computational Systems Biomedicine and contributors.
