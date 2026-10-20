#!/usr/bin/env bash
# Downloads the Synthea-OMOP schema matching benchmark of OMAP (SMAT, Zhang et al. 2021, https://github.com/JZCS2018/SMAT)
# for the Synthea evaluation (eval.agent.etl.synthea): its 105 matches of the twelve Synthea tables onto the OMOP CDM,
# written per table in the format of GDC-SM (original_paper_variable_names = Synthea column, GDC_format_variable_names
# = omop table.field), and the tables of the Synthea sample data of April 2020, which has every column OMAP matches.
# The tables keep the rows of the first patients, at most MAX_ROWS each. Both are not part of this repository, git
# ignores the folder.
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${MODULE_DIR}/src/test/resources/eval/synthea-omop"
OMAP_URL="https://raw.githubusercontent.com/JZCS2018/SMAT/06026226d92b13d69697657316d1e72a188b0353/datasets/omap/omop_synthea_data.xlsx"
SYNTHEA_URL="https://raw.githubusercontent.com/synthetichealth/synthea-sample-data/2c7d489eb4c8bc967dbfab3499a0f573818880dc/downloads/synthea_sample_data_csv_apr2020.zip"
PATIENTS="${PATIENTS:-100}"
MAX_ROWS="${MAX_ROWS:-1000}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

curl -sSfL -o "${TMP_DIR}/omap.xlsx" "${OMAP_URL}"
curl -sSfL -o "${TMP_DIR}/synthea.zip" "${SYNTHEA_URL}"
python3 -m venv "${TMP_DIR}/venv"
"${TMP_DIR}/venv/bin/pip" install --quiet openpyxl==3.1.5
mkdir -p "${OUTPUT_DIR}/source-tables" "${OUTPUT_DIR}/ground-truth"

"${TMP_DIR}/venv/bin/python" -I - "${TMP_DIR}/omap.xlsx" "${TMP_DIR}/synthea.zip" "${OUTPUT_DIR}" "${PATIENTS}" "${MAX_ROWS}" <<'PYEOF'
import csv
import io
import sys
import zipfile

import openpyxl

omap_path, synthea_path, output_dir, patients, max_rows = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])


def key(name):
    """OMAP writes the Synthea columns in lower case and with spaces, e.g. 'body site code' for BODYSITE_CODE."""
    return name.lower().replace(" ", "").replace("_", "")


# Columns of OMAP: omop, table, des1, des2, label, ... with omop = 'table-field' of OMOP and table = of Synthea
matches = {}
rows = openpyxl.load_workbook(omap_path, read_only=True, data_only=True).worksheets[0].iter_rows(values_only=True)
next(rows)
for row in rows:
    table, column = row[1].split("-", 1)
    matches.setdefault(table, [])
    if str(row[4]) == "1":
        matches[table].append((column, row[0].replace("-", ".", 1)))

archive = zipfile.ZipFile(synthea_path)


def read(table):
    with archive.open(f"csv/{table}.csv") as f:
        return list(csv.reader(io.TextIOWrapper(f, encoding="utf-8")))


kept = {row[0] for row in read("patients")[1:patients + 1]}
for table, pairs in matches.items():
    header, *data = read(table)
    # The rows of the first patients; organizations and providers belong to no patient
    if "PATIENT" in header:
        data = [row for row in data if row[header.index("PATIENT")] in kept]
    elif table == "patients":
        data = [row for row in data if row[0] in kept]
    data = data[:max_rows]
    with open(f"{output_dir}/source-tables/{table}.csv", "w", newline="", encoding="utf-8") as f:
        csv.writer(f).writerows([header] + data)
    columns = {key(name): name for name in header}
    found = [(columns[key(column)], field) for column, field in pairs if key(column) in columns]
    with open(f"{output_dir}/ground-truth/{table}.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["original_paper_variable_names", "GDC_format_variable_names"])
        writer.writerows(found)
    print(f"{table}: {len(data)} rows, {len(header)} columns, {len(found)} of {len(pairs)} matches")
PYEOF
