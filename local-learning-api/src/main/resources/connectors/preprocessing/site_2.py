#!/usr/bin/env python3
"""
Site 2 preprocessing — "numeric coding-system registry".

Simulates an institution whose extract speaks in integer code systems rather
than human-readable labels: HL7-style administrative gender, an in-house race
code book (R1..R5, R9=unknown), and ordinal severity / dosage codes. Columns are
renamed to the site's house style and the clinical columns are reordered.

Run:  python3 site_2.py
Reads canonical fixture clinic_002.csv and writes the heterogeneous export
site_02.csv plus the reverse connector us-130-site-02.json (both alongside the
other connector resources). The connector — built from the very same spec —
maps this site back into the shared schema with configuration alone.
"""
from harmonization_lib import (
    SiteSpec, ColumnPlan, ValueMap, Sentinel, CombineColumns, MEDICATION_COLUMNS, emit,
)

# Ordinal drug-dosage code shared by all 23 medication columns.
DRUG_ORDINAL = {"No": "0", "Down": "1", "Steady": "2", "Up": "3"}

SPEC = SiteSpec(
    site_id=2,
    title="Numeric coding-system registry (HL7 administrative gender, "
          "in-house race codebook, ordinal severity/dosage codes)",
    delimiter=",",
    columns=[
        # HL7 administrative gender: 1 = Male, 2 = Female.
        ColumnPlan("gender", "sex", [ValueMap({"Male": "1", "Female": "2"})]),
        # In-house race code book; R9 is the unknown sentinel.
        ColumnPlan("race", "ethnicity_code", [ValueMap(
            {"Caucasian": "R1", "AfricanAmerican": "R2", "Hispanic": "R3",
             "Asian": "R4", "Other": "R5"}, missing_code="R9")]),
        # HbA1c severity grade (0 not measured .. 3 high).
        ColumnPlan("A1Cresult", "hba1c_class", [ValueMap(
            {"None": "0", "Norm": "1", ">7": "2", ">8": "3"})]),
        # Max glucose serum grade.
        ColumnPlan("max_glu_serum", "glu_serum_class", [ValueMap(
            {"None": "0", "Norm": "1", ">200": "2", ">300": "3"})]),
        # Readmission code (0 none, 1 late >30d, 2 early <30d).
        ColumnPlan("readmitted", "readmission_code", [ValueMap(
            {"NO": "0", ">30": "1", "<30": "2"})]),
        ColumnPlan("change", "med_change_code", [ValueMap({"No": "0", "Ch": "1"})]),
        ColumnPlan("diabetesMed", "on_dm_meds", [ValueMap({"No": "0", "Yes": "1"})]),
        # Free-text / bracket columns: missing encoded as the numeric sentinel -1.
        ColumnPlan("weight", "weight_band", [Sentinel(["-1"])]),
        ColumnPlan("payer_code", "payer", [Sentinel(["-1"])]),
        ColumnPlan("medical_specialty", "specialty", [Sentinel(["-1"])]),
    ] + [
        ColumnPlan(col, f"{col}_rx", [ValueMap(DRUG_ORDINAL)])
        for col in MEDICATION_COLUMNS
    ],
    # Row-level: pack the three ICD-9 diagnosis columns into one pipe-delimited
    # field; the connector splits it back apart with the Split Column row built-in.
    row_ops=[CombineColumns(["diag_1", "diag_2", "diag_3"], "dx_bundle", "|")],
    shuffle_seed=2,
)


if __name__ == "__main__":
    manifest = emit(SPEC)
    print(manifest)
