#!/usr/bin/env python3
"""
Site 4 preprocessing — "symbolic / temporal-encoding EHR".

Simulates an institution that encodes length of stay as an ISO-8601 duration
(P3D), drug-dosage changes as typographic symbols, age as half-open integer
ranges without brackets, and uses terse alphanumeric tokens for labs and
outcomes. Columns are renamed and reordered.

Run:  python3 site_4.py
Reads canonical fixture clinic_004.csv and writes site_04.csv plus the reverse
connector us-130-site-04.json. The connector reverses the temporal encoding
with a duration-to-days built-in and everything else with value maps.
"""
from harmonization_lib import (
    SiteSpec, ColumnPlan, ValueMap, IsoDuration, Sentinel, CombineColumns,
    MEDICATION_COLUMNS, emit,
)

# Typographic dosage symbols for the 23 medication columns.
DRUG_SYMBOL = {"No": "-", "Steady": "=", "Up": "+", "Down": "x"}

# Age brackets rendered as plain integer ranges, e.g. "[0-10)" -> "0-9".
AGE_RANGES = {
    "[0-10)": "0-9", "[10-20)": "10-19", "[20-30)": "20-29", "[30-40)": "30-39",
    "[40-50)": "40-49", "[50-60)": "50-59", "[60-70)": "60-69", "[70-80)": "70-79",
    "[80-90)": "80-89", "[90-100)": "90-99",
}

SPEC = SiteSpec(
    site_id=4,
    title="Symbolic/temporal-encoding EHR (ISO-8601 length-of-stay, typographic "
          "dosage symbols, bracket-free age ranges, terse lab/outcome tokens)",
    delimiter=",",
    columns=[
        ColumnPlan("gender", "Sex", [ValueMap({"Female": "F", "Male": "M"})]),
        ColumnPlan("race", "RaceCode", [ValueMap(
            {"Caucasian": "CAUC", "AfricanAmerican": "AFAM", "Hispanic": "HISP",
             "Asian": "ASIA", "Other": "OTHR"}, missing_code="UNK")]),
        ColumnPlan("age", "AgeRange", [ValueMap(AGE_RANGES)]),
        # Length of stay as an ISO-8601 duration (P{n}D).
        ColumnPlan("time_in_hospital", "StayDuration", [IsoDuration()]),
        ColumnPlan("A1Cresult", "HbA1c", [ValueMap(
            {"None": "NA", "Norm": "WNL", ">7": "H1", ">8": "H2"})]),
        ColumnPlan("max_glu_serum", "GlucoseSerum", [ValueMap(
            {"None": "NA", "Norm": "WNL", ">200": "G2", ">300": "G3"})]),
        ColumnPlan("change", "MedChanged", [ValueMap({"No": "0", "Ch": "1"})]),
        ColumnPlan("diabetesMed", "OnDiabetesMed", [ValueMap(
            {"No": "false", "Yes": "true"})]),
        ColumnPlan("readmitted", "Readmit", [ValueMap(
            {"NO": "N", ">30": "L", "<30": "E"})]),
        ColumnPlan("weight", "WeightRange", [Sentinel(["."])]),
        ColumnPlan("payer_code", "Payer", [Sentinel(["."])]),
        ColumnPlan("medical_specialty", "Specialty", [Sentinel(["."])]),
    ] + [
        ColumnPlan(col, f"Rx_{col.replace('-', '_')}", [ValueMap(DRUG_SYMBOL)])
        for col in MEDICATION_COLUMNS
    ],
    # Row-level: fuse the three encounter administrative codes (admission type /
    # discharge disposition / admission source) into one hyphen-delimited token;
    # the connector splits it back with the Split Column row built-in.
    row_ops=[CombineColumns(
        ["admission_type_id", "discharge_disposition_id", "admission_source_id"],
        "EncounterCodes", "-")],
    shuffle_seed=4,
)


if __name__ == "__main__":
    manifest = emit(SPEC)
    print(manifest)
