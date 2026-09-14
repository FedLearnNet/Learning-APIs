#!/usr/bin/env python3
"""
Site 5 preprocessing — "messy free-text extract".

Simulates the most common real-world nuisance: an extract dumped from a
spreadsheet with inconsistent casing, stray surrounding/embedded whitespace, and
a zoo of missing-value sentinels (NULL, N/A, -99, unknown). No code systems —
just dirtiness. This stresses the *cleaning* built-ins rather than the value
maps.

Run:  python3 site_5.py
Reads canonical fixture clinic_005.csv and writes site_05.csv plus the reverse
connector us-130-site-05.json. The connector reverses the mess with trim/case,
missing-value normalisation and whitespace-stripping built-ins only.
"""
from harmonization_lib import (
    SiteSpec, ColumnPlan, CaseMangle, PadWithMissing, Sentinel, InnerSpaces,
    SplitColumn, MEDICATION_COLUMNS, emit,
)

SPEC = SiteSpec(
    site_id=5,
    title="Messy free-text extract (inconsistent casing, stray whitespace, "
          "heterogeneous missing-value sentinels)",
    delimiter=",",
    columns=[
        # Lower-cased + whitespace-padded labels -> reversed by trim+title-case.
        ColumnPlan("gender", "Gender", [CaseMangle()]),
        ColumnPlan("A1Cresult", "A1Cresult", [CaseMangle()]),
        ColumnPlan("max_glu_serum", "max_glu_serum", [CaseMangle()]),
        ColumnPlan("change", "change", [CaseMangle()]),
        ColumnPlan("diabetesMed", "diabetesMed", [CaseMangle()]),
        # Compact comparator tokens spaced out -> reversed by stripping whitespace.
        ColumnPlan("readmitted", "readmitted", [InnerSpaces()]),
        # Race keeps its (camel-case) spelling but is whitespace-padded; missing is
        # one of several sentinels -> normalise-missing then trim.
        ColumnPlan("race", "race", [PadWithMissing(["NULL", "N/A"])]),
        # Distinct missing sentinels per administrative column.
        ColumnPlan("weight", "weight", [Sentinel(["-99"])]),
        ColumnPlan("payer_code", "payer_code", [Sentinel(["NULL"])]),
        ColumnPlan("medical_specialty", "medical_specialty", [Sentinel(["unknown"])]),
    ] + [
        ColumnPlan(col, col, [CaseMangle()])
        for col in MEDICATION_COLUMNS
    ],
    # Row-level: split the age band into low/high boundary columns; the connector
    # rejoins them with the Merge Columns row built-in.
    row_ops=[SplitColumn("age", ["age_low", "age_high"], "-")],
)


if __name__ == "__main__":
    manifest = emit(SPEC)
    print(manifest)
