# ETL agent evaluation in Docker (DinD)

Runs the evaluations of the ETL agent on a server in one container with its own Docker daemon, like
[FL-Net-CLI/dind](https://github.com/FedLearnNet/FL-Net-CLI/tree/main/dind): the database, the orchestrator and the
tests run inside, and so do the code runner and build pipeline containers the orchestrator starts. They reach the
tests at the gateway of their network, whatever the Docker of the server allows. The container only connects out: the
LLM, the store tools of the global dev server and ghcr; it publishes no port. The schemas come from a datamodeler of the
checkout in dev mode, which imports `datamodeler-api/src/main/resources/datamodeler-imports` with their fixed ids; it
needs the SapBERT model in `datamodeler-api/src/main/resources/sapbert` or `local-learning-api/src/main/resources/sapbert`.

```bash
local-learning-api/eval-dind/run_all.sh      # everything: US-130, GDC-SM (78 and 736 fields), Synthea-OMOP, summary
NAME=v3 local-learning-api/eval-dind/run_all.sh  # runs run-v3-1..3, run-v3-full-1, summary-v3.md; again to continue
local-learning-api/download-gdc-sm.sh        # once, for the GDC benchmark
local-learning-api/download-synthea-omop.sh  # once, for the Synthea-OMOP benchmark
local-learning-api/eval-dind/run.sh          # US-130 and GDC, real and synthetic, 3 runs each
EVAL_TESTS=gdc EVAL_RUNS=1 EVAL_GDC_STUDIES="GDC Krug" local-learning-api/eval-dind/run.sh
EVAL_TESTS=synthea EVAL_MODES=real EVAL_RUNS=1 local-learning-api/eval-dind/run.sh
```

The checkout is mounted, so the tests run its code with its `local-learning-api/.env` (LLM key) and write the results
to `local-learning-api/results/eval/<model>/<mode>/run-<n>`, like `run-us130-eval.sh`. Files the tests create belong
to root. The output of the container goes to `local-learning-api/logs/eval-dind_<time>.log` as well, as the container
removes itself when it is done.

| Variable | Default | Meaning |
|---|---|---|
| `EVAL_TESTS` | `us130,gdc` | Evaluations to run: `us130`, `gdc`, `synthea` |
| `EVAL_MODES`, `EVAL_RUNS`, `EVAL_RUN_NUMBERS`, `EVAL_MODELS`, `EVAL_DATASETS`, `EVAL_REPLACE` | see `run-us130-eval.sh` | Passed on to both evaluations; modes default to `real,synthetic` |
| `EVAL_GDC_STUDIES` | all ten | GDC studies, e.g. `GDC Krug,GDC Clark` |
| `EVAL_SYNTHEA_TABLES` | all twelve | Synthea tables of the Synthea-OMOP benchmark, e.g. `Synthea patients,Synthea encounters` (data: `download-synthea-omop.sh`) |
| `EVAL_GDC_SCHEMA` | the 78 fields of the ground truth | `full` for all 736 GDC fields, like the benchmark; give it own run numbers, e.g. `EVAL_RUN_NUMBERS=full-1` |
| `ORCH_API_IMAGE` | `…/orch-api:feature-pipeline-sast` | Orchestrator with the run modes of the build pipeline |
| `GLOBAL_API_URL` | global dev server | Store tools |
| `GLOBAL_SCHEMA_API_URL` | the datamodeler of eval-dind | Schemas, e.g. `https://dev.federated-learning.net/data-modeler` |

The volumes `eval-dind-maven` and `eval-dind-docker` keep the Maven repository and the pulled images between runs;
`docker volume rm eval-dind-docker` starts the inner Docker empty.
