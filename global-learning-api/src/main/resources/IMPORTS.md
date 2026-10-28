# Tool and workflow imports

Startup loads `tool-imports` first, then `workflow-imports`. Both are enabled in
`flnet.properties`. Missing tool images are rebuilt by the deployment's build
pipeline; these definitions contain no copied image names or scan reports for
the migrated tools.

## Migrated catalog

The 26 `tool-*.json` definitions use the public FedLearnNet repositories and their
current `app.yml` configuration. Their version publish hashes identify the
repository commits used to generate these definitions. They are import lookup
keys; the normal build pipeline resolves the repository revision when it builds.
The existing federated US-130 NN definition remains active as well.

Existing app UUIDs are preserved, including the shared identities of the old
`data-transformer` / `transformer-date` and `gmm` / `gaussianmixturemodel`
records. Those aliases are consolidated into single canonical definitions.
OpenCRAVAT has a new deterministic UUID derived from its repository URL.
The unfinished RDKit scaffold is deliberately excluded from runnable imports.

All original definitions are preserved byte-for-byte as Git moves:

- Previously archived tools: `archived-tool-imports/legacy/`.
- Previous active agglomerative/GMM tools: `archived-tool-imports/previous-active/`.
- Previous test fixtures: `src/test/resources/archived-tool-imports/`.
- Original workflow exports: `archived-workflow-imports/`.

The two neural-network variants outside the preceding repository migration remain
at the top level of `archived-tool-imports` until their real repositories are
migrated. The original archived federated NN is preserved under `legacy`; its
GitHub definition is already active. Archive directories are outside the
configured import paths.

Test fixtures were moved out of `tool-imports` because classpath scanning also
loads test resource roots. Tests now use the main active catalog, while all
original test JSON files remain available in their inactive archive.

## Workflows

All eleven workflows resolve explicit version hashes from the active catalog.
Their updated files have descriptive names under `workflow-imports`; the original
exports retain their original names and contents under `archived-workflow-imports`.
GMM parameters use `output_labels` and `output_responsibilities`; the retired
BIRCH `copy` parameter is removed. Boolean values are JSON booleans.
File connections use the current named ports and matching file types. Uploaded
CSV inputs require a header. The multi-model comparison feeds both models and
the evaluator from the same encoded rows; the shared-input clustering example
also sends the encoded table to agglomerative clustering.

Startup imports preserve an existing workflow with the same name and owner to
avoid duplicate templates and changes to existing execution history. These
resource changes apply to new imports. Existing saved workflows are not rewritten;
use the workflow editor or import a renamed copy when replacing an old graph.

UCI fetch workflows need outbound internet access. OpenCRAVAT needs annotation
modules/databases and MOFA2 needs a trained model supplied at runtime. See the
individual tool READMEs for scientific input requirements. MoSBi and UnPaSt were
validated for packaging and dependencies, not full scientific analyses.

## Validation

Run the focused Java tests from `Learning-APIs`:

```bash
./mvnw -pl global-learning-api -am \
  -Dtest=ToolWorkflowImportResourcesTest,WorkflowExportBOTest \
  -Dsurefire.failIfNoSpecifiedTests=false clean test
```

They check DTO compatibility, unique identities, published version references,
parameter names/types, required input coverage, matching ports/file types,
execution order, startup configuration and duplicate-safe workflow imports.

Validated on 2026-10-28: all five focused Java tests passed. All eleven workflow
graphs also passed local smoke execution with the real nonroot tool containers
and CSV file handoffs (19 distinct node executions, with identical nodes reused).
Fetch nodes downloaded public UCI dataset 17; upload-based examples used synthetic
CSV fixtures. This checks the tool implementations and wiring, rather than a live
platform deployment. Temporary logs/results are in
`/private/tmp/fl-net-workflow-smoke` and `/private/tmp/fl-net-active-import-tests.log`.
