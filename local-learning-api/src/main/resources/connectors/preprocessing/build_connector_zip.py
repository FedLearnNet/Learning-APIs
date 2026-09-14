#!/usr/bin/env python3
"""Build a connector ZIP holding only the MIMIC-IV tables the diabetes connector reads.

The merge costs the importer one row per source row, whether or not the connector
maps anything from that table — and the tables it never touches (chartevents, emar,
poe, ...) are the bulk of the data. Keeping only the needed ones leaves the imported
result identical and removes ~90% of the work.

  Dropped tables are still written as HEADER-ONLY sheets. The merge prefixes a
  column with its sheet name only when the column occurs in more than one sheet
  (TabularFileReaderBO#findDuplicateNonUidColumnsAcrossHeaders), so simply deleting
  a table renames columns elsewhere: without chartevents, `labevents::valuenum`
  becomes `valuenum` and the connector stops finding it. Keeping every header means
  the column names stay exactly as in the full archive; a header-only sheet
  contributes no rows to the merge.

Usage:
  ./build_connector_zip.py <source folder> [output.zip]

Example:
  ./decompress_gz.sh mimic-iv-full-2.2
  ./build_connector_zip.py mimic-iv-full-2.2 mimic-iv-full-2.2-slim.zip
"""

import csv
import io
import os
import sys
import zipfile

# Tables the connector reads. Everything the shared schema asks for is derived
# from these native MIMIC-IV tables by the connector itself.
KEEP = [
    "admissions",
    "diagnoses_icd",
    "labevents",
    "omr",
    "patients",
    "prescriptions",
    "procedures_icd",
    "services",
]

# Row filter per table: {table: (column, {values to keep})}. Everything else is
# dropped, except EXTRA_ROWS_PER_PATIENT rows per patient that are kept on
# purpose, so the connector's own "Filter Rows By Value" step still has something
# to remove and the filtering stays visible in the import.
#
#   Careful: the connector counts rows of these tables — Num Lab Procedures is the
#   number of labevents rows of an encounter — and a filtered archive makes those
#   counts describe what is left, not what the hospital did. Filter here for a
#   small demo archive; leave it empty when the counts have to be real.
ROW_FILTERS = {
    "labevents": ("itemid", {"50852", "50931", "50809"}),
    "omr": ("result_name", {"Weight (Lbs)"}),
}
EXTRA_ROWS_PER_PATIENT = 2
PATIENT_COLUMN = "subject_id"

# Schema fields whose value is a count of rows in a filtered table.
COUNTED_BY_CONNECTOR = {
    "labevents": "Num Lab Procedures",
    "procedures_icd": "Num Procedures",
    "diagnoses_icd": "Number Diagnoses",
    "prescriptions": "Num Medications",
}

TABLE_SUFFIXES = (".csv", ".tsv", ".txt", ".psv", ".dat", ".tab", ".dsv")


def header_of(path):
    with open(path, newline="", encoding="utf-8", errors="replace") as handle:
        line = handle.readline().rstrip("\r\n")
    return next(csv.reader([line])) if line else []


def write_filtered(archive, arcname, path, column, values):
    """Copies a table into the archive, keeping the wanted rows plus a few others."""
    kept = dropped = 0
    extra = {}
    with open(path, newline="", encoding="utf-8", errors="replace") as source, \
            archive.open(arcname, "w") as raw:
        target = io.TextIOWrapper(raw, encoding="utf-8", newline="")
        reader = csv.reader(source)
        writer = csv.writer(target, lineterminator="\n")

        header = next(reader, None)
        if header is None:
            return 0, 0
        writer.writerow(header)
        index = header.index(column) if column in header else -1
        patient = header.index(PATIENT_COLUMN) if PATIENT_COLUMN in header else -1
        if index < 0:
            sys.exit("Column '%s' not found in %s" % (column, os.path.basename(path)))

        for row in reader:
            if index < len(row) and row[index] in values:
                writer.writerow(row)
                kept += 1
                continue
            key = row[patient] if 0 <= patient < len(row) else ""
            if extra.get(key, 0) < EXTRA_ROWS_PER_PATIENT:
                extra[key] = extra.get(key, 0) + 1
                writer.writerow(row)
                kept += 1
            else:
                dropped += 1
        target.flush()
        target.detach()
    return kept, dropped


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        sys.exit(__doc__)

    source = os.path.abspath(sys.argv[1].rstrip(os.sep))
    if not os.path.isdir(source):
        sys.exit("Not a directory: %s" % source)
    out = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else source + "-connector.zip")

    tables = []
    for root, _dirs, files in os.walk(source):
        for name in sorted(files):
            if name.endswith(".gz"):
                sys.exit("Found %s — decompress first:\n  ./decompress_gz.sh %s" % (name, source))
            if name.lower().endswith(TABLE_SUFFIXES):
                tables.append(os.path.join(root, name))
    if not tables:
        sys.exit("No delimited source tables found under %s" % source)

    prefix = os.path.basename(source)
    kept = set()
    filtered = []
    print("Writing %s" % out)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for path in tables:
            name = os.path.basename(path)
            sheet = os.path.splitext(name)[0]
            arcname = "%s/%s" % (prefix, name)
            if sheet not in KEEP:
                archive.writestr(arcname, ",".join(header_of(path)) + "\n")
                print("  header %s" % sheet)
                continue

            kept.add(sheet)
            if sheet in ROW_FILTERS:
                column, values = ROW_FILTERS[sheet]
                rows, dropped = write_filtered(archive, arcname, path, column, values)
                filtered.append((sheet, rows, dropped))
                print("  filter %-24s %8d rows kept, %d dropped (%s)" % (sheet, rows, dropped, column))
            else:
                archive.write(path, arcname)
                print("  full   %-24s %8.1f MB" % (sheet, os.path.getsize(path) / 1_048_576))

    missing = [sheet for sheet in KEEP if sheet not in kept]
    if missing:
        print("\nNot present in this source: %s" % ", ".join(missing))
    for sheet, _rows, _dropped in filtered:
        if sheet in COUNTED_BY_CONNECTOR:
            print("\nNOTE: the connector derives '%s' by counting %s rows, so that field now describes\n"
                  "      the filtered archive rather than the full record."
                  % (COUNTED_BY_CONNECTOR[sheet], sheet))
    print("\n%s: %.1f MB" % (os.path.basename(out), os.path.getsize(out) / 1_048_576))


if __name__ == "__main__":
    main()
