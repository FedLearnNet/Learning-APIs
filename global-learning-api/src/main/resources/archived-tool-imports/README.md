# Original and unmigrated tool imports

All original JSON definitions are preserved. Startup uses `../tool-imports`;
this archive is not part of its configured import paths.

- `legacy/`: the original archived definitions, moved without content changes.
- `previous-active/`: original agglomerative/GMM definitions from the active catalog.
- `us130-hospital-readmission-nn.json` and
  `us130-hospital-readmission-prediction.json`: variants whose repositories were
  outside the selected dAIbetes migration; they remain inactive.

The original test fixtures are preserved separately under
`src/test/resources/archived-tool-imports`. See `../IMPORTS.md` for the active
catalog and workflow migration.
