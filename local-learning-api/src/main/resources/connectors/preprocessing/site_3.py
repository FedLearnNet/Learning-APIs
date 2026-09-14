#!/usr/bin/env python3
"""
Site 3 preprocessing — "European clinical information system".

Simulates a non-English institution: a semicolon-delimited export, German
category labels, and a different unit for length of stay (hours instead of
days). Columns are renamed to German field names.

Run:  python3 site_3.py
Reads canonical fixture clinic_003.csv and writes site_03.csv plus the reverse
connector us-130-site-03.json. The connector reverses the unit change with a
numeric rescale built-in and the language differences with value maps; the
semicolon delimiter is handled declaratively by the connector input config.
"""
from harmonization_lib import (
    SiteSpec, ColumnPlan, ValueMap, Scale, Sentinel, SplitColumn,
    MEDICATION_COLUMNS, emit,
)

# German drug-dosage vocabulary for the 23 medication columns.
DRUG_DE = {"No": "Nein", "Down": "Reduziert", "Steady": "Stabil", "Up": "Erhoeht"}

SPEC = SiteSpec(
    site_id=3,
    title="European clinical information system (semicolon-delimited, German "
          "category vocabulary, length-of-stay in hours)",
    delimiter=";",
    columns=[
        ColumnPlan("gender", "geschlecht", [ValueMap(
            {"Female": "weiblich", "Male": "maennlich"})]),
        ColumnPlan("race", "herkunft", [ValueMap(
            {"Caucasian": "Kaukasisch", "AfricanAmerican": "Afroamerikanisch",
             "Hispanic": "Hispanisch", "Asian": "Asiatisch", "Other": "Andere"},
            missing_code="Unbekannt")]),
        # Length of stay reported in HOURS (canonical is days) -> ×24.
        ColumnPlan("time_in_hospital", "verweildauer_stunden", [Scale(24)]),
        ColumnPlan("A1Cresult", "hba1c_befund", [ValueMap(
            {"None": "Nicht gemessen", "Norm": "Normal", ">7": "Ueber 7",
             ">8": "Ueber 8"})]),
        ColumnPlan("max_glu_serum", "glukose_serum", [ValueMap(
            {"None": "Nicht gemessen", "Norm": "Normal", ">200": "Ueber 200",
             ">300": "Ueber 300"})]),
        ColumnPlan("change", "medikationsaenderung", [ValueMap(
            {"No": "Unveraendert", "Ch": "Geaendert"})]),
        ColumnPlan("diabetesMed", "diabetes_medikation", [ValueMap(
            {"No": "Nein", "Yes": "Ja"})]),
        ColumnPlan("readmitted", "wiederaufnahme", [ValueMap(
            {"NO": "Keine", ">30": "Nach 30 Tagen", "<30": "Binnen 30 Tagen"})]),
        ColumnPlan("weight", "gewicht", [Sentinel(["k.A."])]),
        ColumnPlan("payer_code", "kostentraeger", [Sentinel(["k.A."])]),
        ColumnPlan("medical_specialty", "fachgebiet", [Sentinel(["k.A."])]),
    ] + [
        ColumnPlan(col, f"med_{col}", [ValueMap(DRUG_DE)])
        for col in MEDICATION_COLUMNS
    ],
    # Row-level: split the age band "[50-60)" into two boundary columns; the
    # connector rejoins them with the Merge Columns row built-in.
    row_ops=[SplitColumn("age", ["alter_von", "alter_bis"], "-")],
)


if __name__ == "__main__":
    manifest = emit(SPEC)
    print(manifest)
