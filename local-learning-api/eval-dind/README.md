# ETL agent evaluation in Docker (DinD)

Runs the evaluations of the ETL agent on a server in one container with its own Docker daemon, like
[FL-Net-CLI/dind](https://github.com/FedLearnNet/FL-Net-CLI/tree/main/dind): the database, the orchestrator and the
tests run inside, and so do the code runner and build pipeline containers the orchestrator starts. They reach the
tests at the gateway of their network, whatever the Docker of the server allows. The container only connects out: the
LLM, the schemas and store tools of the global dev server and ghcr; it publishes no port.

```bash
local-learning-api/download-gdc-sm.sh        # once, for the GDC benchmark
local-learning-api/eval-dind/run.sh          # US-130 and GDC, real and synthetic, 3 runs each
EVAL_TESTS=gdc EVAL_RUNS=1 EVAL_GDC_STUDIES="GDC Krug" local-learning-api/eval-dind/run.sh
```

The checkout is mounted, so the tests run its code with its `local-learning-api/.env` (LLM key) and write the results
to `local-learning-api/results/eval/<model>/<mode>/run-<n>`, like `run-us130-eval.sh`. Files the tests create belong
to root.

| Variable | Default | Meaning |
|---|---|---|
| `EVAL_TESTS` | `us130,gdc` | Evaluations to run |
| `EVAL_MODES`, `EVAL_RUNS`, `EVAL_RUN_NUMBERS`, `EVAL_MODELS`, `EVAL_DATASETS`, `EVAL_REPLACE` | see `run-us130-eval.sh` | Passed on to both evaluations; modes default to `real,synthetic` |
| `EVAL_GDC_STUDIES` | all ten | GDC studies, e.g. `GDC Krug,GDC Clark` |
| `ORCH_API_IMAGE` | `…/orch-api:feature-pipeline-sast` | Orchestrator with the run modes of the build pipeline |
| `GLOBAL_API_URL`, `GLOBAL_SCHEMA_API_URL` | global dev server | Store tools and schemas |

The volumes `eval-dind-maven` and `eval-dind-docker` keep the Maven repository and the pulled images between runs;
`docker volume rm eval-dind-docker` starts the inner Docker empty.
