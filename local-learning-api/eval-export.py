#!/usr/bin/env python3
"""The expert review package of the ETL agent evaluation (see eval-dind/run_protocol.sh).

Collects every mapping of the selected runs below a results folder, rates identical mappings of a target once (same
source columns, same chain and code), hides model, setting, run and the automated result, and shuffles them:

    python3 local-learning-api/eval-export.py [results/eval] --runs <name> [--out results/expert-review-<name>]

    <out>/for-expert/review.html         one page per mapping, rated in the browser, ratings saved as CSV
    <out>/for-expert/ratings.csv         the same ratings as a spreadsheet, for experts who prefer one
    <out>/for-expert/column-reference.csv  MIMIC-IV: the reference columns per target, for the expert to confirm
    <out>/for-expert/README.txt          instructions and rubric
    <out>/for-expert-later/              a random share rated again two weeks later (intra-rater agreement)
    <out>/key.csv, key.json              not for the expert: which runs and automated results stand behind each ID
"""
import argparse
import csv
import html
import importlib.util
import json
import random
import re
import sys
from collections import defaultdict
from pathlib import Path

MODULE = Path(__file__).parent
RUN = re.compile(r"^run-(?:(?P<setting>.+)-)?(?P<number>\d+)$")
BENCHMARK_TABLES = {"gdc-benchmark.json": ("GDC-SM", "gdc", lambda study: study.removeprefix("GDC ") + ".csv"),
                    "synthea-omop-benchmark.json": ("Synthea-OMOP", "synthea-omop",
                                                    lambda study: study.removeprefix("Synthea ") + ".csv")}
SOURCE_VALUES = 20

# Reference connectors and source selection, the same as in the summary; no __pycache__ in the checkout
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("eval_summary", MODULE / "eval-summary.py")
summary = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(summary)


def runs_of(root, name):
    for folder in sorted(path for path in root.rglob("run-*") if path.is_dir()):
        match = RUN.match(folder.name)
        if match and (name is None or match.group("setting") == name):
            yield folder, {"model": str(folder.parent.parent.relative_to(root)), "mode": folder.parent.name,
                           "run": folder.name}


def cohort_items(folder):
    file = folder / "transformer-writer.json"
    if not file.exists():
        return
    for dataset, nodes in json.loads(file.read_text()).items():
        reference = summary.reference_columns(summary.REFERENCE_CONNECTORS.get(dataset, ""))
        for node in nodes:
            review = node["review"]
            verdict = summary.matching_field(review["schemaNode"], reference.get(review["schemaNode"], []), review)
            yield ("Cohort import", dataset, review["schemaNode"], review,
                   {"column verdict": verdict["verdict"] if reference else None,
                    "reference columns": verdict["expected"] if reference else None,
                    "distribution shift": review.get("truthShift")})


def harmonization_items(folder):
    file = folder / "harmonization-file.json"
    if not file.exists():
        return
    for dataset in json.loads(file.read_text()):
        fields = {field["field"]: field for field in summary.harmonization_fields(dataset)} \
            if dataset["dataset"] in summary.REFERENCE_CONNECTORS else {}
        for column in dataset["columns"]:
            field = fields.get(column["column"], {})
            yield ("File harmonization", dataset["dataset"], column["column"], column["review"] or {},
                   {"column verdict": field.get("verdict"), "reference columns": field.get("expected"),
                    "rows differing from reference": column.get("truthShift")})


def benchmark_items(folder, absences):
    for name, (label, root, table) in BENCHMARK_TABLES.items():
        file = folder / name
        if not file.exists():
            continue
        for study, fields in json.loads(file.read_text()).items():
            for field in fields:
                # The ground truth decides the fields without a mapping, unless --benchmark-absences
                if field["verdict"] in ("MISSED", "EMPTY") and not absences:
                    continue
                review = field.get("review") or fallback_review(field, MODULE / "src" / "test" / "resources" / "eval"
                                                                / root / "source-tables" / table(study))
                yield (label, study, field["field"], review,
                       {"column verdict": field["verdict"], "reference columns": field["expected"]})


def fallback_review(field, table):
    """Results written before the benchmark kept reviews: the columns with their values from the source table."""
    found = field["verdict"] in ("CORRECT", "WRONG_COLUMN", "EXTRA")
    columns = []
    if table.exists() and field["mapped"]:
        with table.open(newline="", encoding="utf-8", errors="replace") as file:
            rows = list(csv.DictReader(file))
        for column in field["mapped"]:
            counts = defaultdict(int)
            for row in rows:
                value = next((value for key, value in row.items() if key and key.strip() == column), None)
                if value not in (None, ""):
                    counts[value] += 1
            top = sorted(counts.items(), key=lambda item: -item[1])[:SOURCE_VALUES]
            columns.append(f"{column} ({len(rows)} rows, {len(counts)} distinct values): "
                           + ", ".join(f"'{value}' x{count}" for value, count in top))
    return {"schemaNode": field["field"], "description": None, "dataType": None, "found": found,
            "notFoundReason": None if found else field["configuration"].removeprefix("not found: "),
            "sourceColumns": columns,
            "transformations": [{"configuration": field["configuration"], "code": None}] if found else []}


def signature(task, dataset, target, review):
    # Identical mappings of a target are rated once: the same columns, chain and code; an absence once per target
    if not review.get("found"):
        return task, dataset, target, "absence"
    chain = tuple((step.get("configuration"), step.get("code")) for step in review.get("transformations", []))
    return task, dataset, target, tuple(sorted(summary.review_columns(review))), chain


def collect(root, name, absences=False):
    items = {}
    for folder, setting in runs_of(root, name):
        for task, dataset, target, review, automated in [*cohort_items(folder), *harmonization_items(folder),
                                                         *benchmark_items(folder, absences)]:
            key = signature(task, dataset, target, review)
            item = items.setdefault(key, {"task": task, "dataset": dataset, "target": target, "review": review,
                                          "occurrences": []})
            item["occurrences"].append({**setting, **automated})
    return list(items.values())


def sheet(item):
    """What the expert sees of a mapping: no model, setting, run or automated result."""
    review = item["review"]
    columns = []
    for text in review.get("sourceColumns", []):
        name, _, rest = text.partition(" (")
        columns.append({"name": name, "profile": "(" + rest if rest else ""})
    return {
        "id": item["id"], "task": item["task"], "dataset": item["dataset"], "target": item["target"],
        "description": review.get("description"), "dataType": review.get("dataType"),
        "kind": "mapping" if review.get("found") else "absence",
        "reason": review.get("notFoundReason"),
        "columns": columns,
        "steps": [{"configuration": step.get("configuration"), "code": step.get("code")}
                  for step in review.get("transformations", [])],
        "visit": review.get("visitMapping"),
        "values": review.get("importedValues") or {},
        "rejections": review.get("rejections") or {},
    }


RUBRIC = """\
Source selection (mappings only)
  correct            the selected columns record the target, and no needed column is missing
  partially correct  a needed column is missing or an extra column is read, but the main column is right
  incorrect          the columns record another fact, e.g. alcohol use for smoking status

Transformation (mappings only)
  correct for all values  every source value becomes the right target value, missing markers become empty
  minor deviations        a few rare values are wrong, or a meaningful value (e.g. "not measured") is dropped although
                          the target could not hold it anyway
  incorrect               codes are swapped, units or scales are wrong, or a meaningful value is lost

Reported absence (targets without a mapping)
  justified    no column of the source records the target
  unjustified  a column records it; name it in the comment
"""

README = """\
Expert review of AcETL mappings
===============================

Open review.html in a browser (Firefox, Chrome, Safari or Edge; no internet needed). Each page shows one mapping that
the assistant proposed: the target with its definition and data type, the source columns it selected with their most
frequent values, the transformation (built-in functions, tools, or custom Python code), and the values it produced.
If the assistant found no column, the page shows the target, its reason and the columns of the source.

Please rate each page with the buttons. Your ratings are kept in this browser while you work; use "Download ratings"
regularly and at the end, and send the downloaded CSV back. Instead of the browser, you can enter the same ratings in
ratings.csv; the IDs are the same.

You do not see which model or setting produced a mapping, or whether it agrees with a reference; please do not try to
find out. Take about one to two minutes per page. Leave a comment whenever a rating is not "correct" or "justified".

Please also check column-reference.csv: for each MIMIC-IV target, the columns we take as the
reference. Write "yes" if they are right, or the right columns otherwise.

The folder for-expert-later is sent to you about two weeks after you finish; it repeats some pages with new IDs.

Rubric
------
""" + RUBRIC

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title>
<style>
:root{{--ink:#1d1d1f;--muted:#5b5f66;--line:#d4d8de;--soft:#f4f6f9;--accent:#2d5d9f;--good:#2e7d32;--bad:#b3261e}}
*{{box-sizing:border-box}}body{{margin:0;font:15px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;color:var(--ink);background:#fff}}
header{{position:sticky;top:0;background:#fff;border-bottom:1px solid var(--line);padding:10px 20px;display:flex;gap:16px;
align-items:center;flex-wrap:wrap;z-index:2}}
header h1{{font-size:17px;margin:0 12px 0 0}}.bar{{flex:1;min-width:160px;height:8px;background:var(--soft);border-radius:4px}}
.bar i{{display:block;height:100%;background:var(--good);border-radius:4px}}
button,select{{font:inherit;padding:5px 10px;border:1px solid var(--line);border-radius:6px;background:#fff;cursor:pointer}}
button.primary{{background:var(--accent);border-color:var(--accent);color:#fff}}
main{{max-width:980px;margin:0 auto;padding:16px 20px 80px}}
details.help{{background:var(--soft);border-radius:8px;padding:8px 14px;margin-bottom:16px}}details.help pre{{white-space:pre-wrap;font-size:13px}}
article{{border:1px solid var(--line);border-radius:10px;padding:16px 18px;margin:0 0 18px}}
article.done{{border-left:5px solid var(--good)}}
.top{{display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap}}.id{{font-weight:700;font-size:16px}}
.tag{{font-size:12px;color:var(--muted);border:1px solid var(--line);border-radius:10px;padding:1px 8px;margin-left:6px}}
h2{{font-size:18px;margin:6px 0 2px}}h3{{font-size:13px;text-transform:uppercase;letter-spacing:.04em;color:var(--muted);margin:14px 0 4px}}
.def{{color:var(--muted)}}table{{border-collapse:collapse;width:100%;font-size:13.5px}}td,th{{border-bottom:1px solid var(--line);
padding:4px 6px;text-align:left;vertical-align:top}}th{{color:var(--muted);font-weight:600}}
pre{{background:var(--soft);border-radius:6px;padding:8px 10px;overflow-x:auto;font-size:12.5px;margin:4px 0}}
.step{{font-family:ui-monospace,Menlo,monospace;font-size:12.5px;background:var(--soft);border-radius:6px;padding:6px 8px;margin:4px 0;
word-break:break-word}}
.rate{{display:grid;grid-template-columns:150px 1fr;gap:6px 12px;align-items:center;margin-top:14px;border-top:1px dashed var(--line);padding-top:12px}}
.rate label{{margin-right:12px;white-space:nowrap}}textarea{{width:100%;font:inherit;min-height:46px;border:1px solid var(--line);border-radius:6px;padding:6px}}
.values{{max-height:260px;overflow:auto}}
@media (max-width:640px){{.rate{{grid-template-columns:1fr}}}}
@media print{{header,.rate button{{display:none}}article{{break-inside:avoid}}}}
</style></head><body>
<header><h1>{title}</h1><span id="count"></span><div class="bar"><i id="progress"></i></div>
<select id="filter"><option value="all">All pages</option><option value="open">Not rated yet</option></select>
<button id="load">Load ratings</button><input id="file" type="file" accept=".csv" hidden>
<button class="primary" id="download">Download ratings</button></header>
<main><details class="help"><summary><b>How to rate</b> (rubric)</summary><pre>{rubric}</pre></details><div id="pages"></div></main>
<script>
const ITEMS = {items};
const KEY = "acetl-review-" + {store};
const SCALES = {{
  source: ["correct", "partially correct", "incorrect"],
  transformation: ["correct for all values", "minor deviations", "incorrect"],
  absence: ["justified", "unjustified"]}};
let ratings = {{}};
try {{ ratings = JSON.parse(localStorage.getItem(KEY) || "{{}}"); }} catch (e) {{ ratings = {{}}; }}
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}}[c]));
const save = () => {{ try {{ localStorage.setItem(KEY, JSON.stringify(ratings)); }} catch (e) {{}} progress(); }};
const needed = item => item.kind === "mapping" ? ["source", "transformation"] : ["absence"];
const done = item => needed(item).every(k => (ratings[item.id] || {{}})[k]);
function radios(item, key) {{
  return SCALES[key].map(v => `<label><input type="radio" name="${{item.id}}-${{key}}" value="${{esc(v)}}"
    ${{(ratings[item.id] || {{}})[key] === v ? "checked" : ""}}> ${{esc(v)}}</label>`).join("");
}}
function values(map) {{
  const rows = Object.entries(map);
  return rows.length ? `<div class="values"><table><tr><th>Value</th><th>Count</th></tr>${{rows.map(([v, n]) =>
    `<tr><td>${{esc(v)}}</td><td>${{n}}</td></tr>`).join("")}}</table></div>` : "<p class=def>none</p>";
}}
function page(item) {{
  const cols = item.columns.map(c => `<tr><td><b>${{esc(c.name)}}</b></td><td>${{esc(c.profile)}}</td></tr>`).join("");
  const steps = item.steps.map((s, i) => `<div class="step">${{i + 1}}. ${{esc(s.configuration)}}</div>` +
    (s.code ? `<pre>${{esc(s.code)}}</pre>` : "")).join("") || "<p>Mapped directly, without a transformation.</p>";
  const visit = item.visit && (item.visit.visitIdMapping || item.visit.visitTimestampMapping) ? `<p class=def>Visit: column
    ${{esc(item.visit.visitIdMapping || "-")}}, time ${{esc(item.visit.visitTimestampMapping || "-")}}
    ${{esc(item.visit.timestampFormat || "")}}</p>` : "";
  const body = item.kind === "mapping" ? `
    <h3>Selected source columns and their most frequent values</h3><table>${{cols || "<tr><td>-</td></tr>"}}</table>
    <h3>Transformation</h3>${{steps}}${{visit}}
    ${{Object.keys(item.values).length ? `<h3>Values produced</h3>${{values(item.values)}}` : ""}}
    ${{Object.keys(item.rejections).length ? `<h3>Values rejected by the data type</h3>${{values(item.rejections)}}` : ""}}
    <div class="rate"><b>Source selection</b><div>${{radios(item, "source")}}</div>
    <b>Transformation</b><div>${{radios(item, "transformation")}}</div>`
  : `<h3>Reason given by the assistant</h3><p>${{esc(item.reason || "none")}}</p>
    <details><summary>Columns of the source (${{item.columns.length}})</summary><table>${{cols}}</table></details>
    <div class="rate"><b>Reported absence</b><div>${{radios(item, "absence")}}</div>`;
  return `<article id="${{item.id}}" class="${{done(item) ? "done" : ""}}"><div class="top"><span class="id">${{item.id}}
    <span class="tag">${{esc(item.task)}}</span><span class="tag">${{esc(item.dataset)}}</span></span>
    <span class="tag">${{item.kind === "mapping" ? "Mapping" : "No column found"}}</span></div>
    <h2>${{esc(item.target)}}</h2>${{item.description ? `<div class="def">${{esc(item.description)}}</div>` : ""}}
    ${{item.dataType ? `<div class="def"><b>Data type:</b> ${{esc(item.dataType)}}</div>` : ""}}
    ${{body}}<b>Comment</b><textarea data-id="${{item.id}}">${{esc((ratings[item.id] || {{}}).comment || "")}}</textarea></div></article>`;
}}
function render() {{
  const open = document.getElementById("filter").value === "open";
  document.getElementById("pages").innerHTML = ITEMS.filter(i => !open || !done(i)).map(page).join("");
  progress();
}}
function progress() {{
  const n = ITEMS.filter(done).length;
  document.getElementById("count").textContent = `${{n}} of ${{ITEMS.length}} rated`;
  document.getElementById("progress").style.width = (100 * n / ITEMS.length) + "%";
  ITEMS.forEach(i => {{ const a = document.getElementById(i.id); if (a) a.classList.toggle("done", done(i)); }});
}}
document.addEventListener("change", e => {{
  if (e.target.type !== "radio") return;
  const [id, key] = [e.target.name.slice(0, e.target.name.lastIndexOf("-")), e.target.name.slice(e.target.name.lastIndexOf("-") + 1)];
  (ratings[id] = ratings[id] || {{}})[key] = e.target.value; save();
}});
document.addEventListener("input", e => {{
  if (!e.target.dataset.id) return;
  (ratings[e.target.dataset.id] = ratings[e.target.dataset.id] || {{}}).comment = e.target.value; save();
}});
const COLUMNS = ["id", "task", "dataset", "target", "kind", "source_selection", "transformation", "absence", "comment"];
const cell = v => `"${{String(v ?? "").replace(/"/g, '""')}}"`;
document.getElementById("download").onclick = () => {{
  const lines = [COLUMNS.join(",")].concat(ITEMS.map(i => {{ const r = ratings[i.id] || {{}};
    return [i.id, i.task, i.dataset, i.target, i.kind, r.source, r.transformation, r.absence, r.comment].map(cell).join(","); }}));
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob(["\\ufeff" + lines.join("\\r\\n")], {{type: "text/csv"}}));
  a.download = {download}; a.click();
}};
document.getElementById("load").onclick = () => document.getElementById("file").click();
document.getElementById("file").onchange = async e => {{
  const text = await e.target.files[0].text();
  const rows = text.replace(/^\\ufeff/, "").split(/\\r?\\n/).slice(1).filter(Boolean).map(line =>
    [...line.matchAll(/("([^"]|"")*"|[^,]*)(,|$)/g)].map(m => m[1].replace(/^"|"$/g, "").replace(/""/g, '"')));
  rows.forEach(r => {{ if (r[0]) ratings[r[0]] = {{source: r[5] || undefined, transformation: r[6] || undefined,
    absence: r[7] || undefined, comment: r[8] || ""}}; }});
  save(); render();
}};
document.getElementById("filter").onchange = render;
render();
</script></body></html>
"""


def write_review(folder, title, store, sheets):
    folder.mkdir(parents=True, exist_ok=True)
    data = json.dumps(sheets, ensure_ascii=False).replace("</", "<\\/")
    (folder / "review.html").write_text(PAGE.format(title=html.escape(title), rubric=html.escape(RUBRIC), items=data,
                                                    store=json.dumps(store), download=json.dumps(store + "-ratings.csv")),
                                        encoding="utf-8")
    with (folder / "ratings.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["id", "task", "dataset", "target", "kind", "source_selection", "transformation", "absence",
                         "comment"])
        for item in sheets:
            writer.writerow([item["id"], item["task"], item["dataset"], item["target"], item["kind"], "", "", "", ""])
    (folder / "README.txt").write_text(README, encoding="utf-8")


def write_column_reference(folder, items):
    """The MIMIC-IV targets with the columns of the reference connector, for the expert to confirm before the runs."""
    reference = summary.reference_columns(summary.REFERENCE_CONNECTORS["MIMIC IV Demo"])
    definitions = {}
    for item in items:
        if item["task"] == "Cohort import":
            definitions.setdefault(item["target"], (item["review"].get("description"), item["review"].get("dataType")))
    targets = sorted(set(reference) | set(definitions))
    with (folder / "column-reference.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["target", "description", "data type", "reference columns (MIMIC-IV demo)", "right? (yes/no)",
                         "right columns, if not"])
        for target in targets:
            description, data_type = definitions.get(target, (None, None))
            writer.writerow([target, description or "", data_type or "", ", ".join(reference.get(target, [])) or "(none)",
                             "", ""])


def write_key(out, items, repeats):
    out.mkdir(parents=True, exist_ok=True)
    (out / "key.json").write_text(json.dumps([{key: value for key, value in item.items() if key != "review"}
                                              | {"repeat": repeats.get(item["id"])} for item in items],
                                             indent=1, ensure_ascii=False), encoding="utf-8")
    with (out / "key.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["id", "repeat id", "task", "dataset", "target", "kind", "occurrences", "settings",
                         "column verdicts", "reference columns"])
        for item in items:
            occurrences = item["occurrences"]
            writer.writerow([item["id"], repeats.get(item["id"], ""), item["task"], item["dataset"], item["target"],
                             "mapping" if item["review"].get("found") else "absence", len(occurrences),
                             "; ".join(f"{o['model']}/{o['mode']}/{o['run']}" for o in occurrences),
                             "; ".join(sorted({str(o.get('column verdict')) for o in occurrences})),
                             ", ".join(occurrences[0].get("reference columns") or [])])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", nargs="?", default=str(MODULE / "results" / "eval"))
    parser.add_argument("--runs", help="only the runs of this name, e.g. protocol for run-protocol-1..3")
    parser.add_argument("--out", help="output folder, default results/expert-review-<runs>")
    parser.add_argument("--repeat", type=float, default=0.1, help="share rated again later, default 0.1")
    parser.add_argument("--benchmark-absences", action="store_true",
                        help="also rate the benchmark fields without a mapping, which the ground truth already decides")
    parser.add_argument("--seed", type=int, default=20261022, help="seed of the shuffling and the repeat sample")
    args = parser.parse_args()

    out = Path(args.out or MODULE / "results" / f"expert-review-{args.runs or 'all'}")
    items = collect(Path(args.root), args.runs, args.benchmark_absences)
    if not items:
        parser.error(f"no results of runs '{args.runs}' below {args.root}")
    random.Random(args.seed).shuffle(items)
    for number, item in enumerate(items, 1):
        item["id"] = f"M-{number:04d}"
    sample = random.Random(args.seed + 1).sample(items, max(1, round(args.repeat * len(items))))
    random.Random(args.seed + 2).shuffle(sample)
    repeats = {item["id"]: f"R-{number:04d}" for number, item in enumerate(sample, 1)}

    write_review(out / "for-expert", "AcETL expert review", f"{args.runs or 'all'}", [sheet(item) for item in items])
    write_column_reference(out / "for-expert", items)
    write_review(out / "for-expert-later", "AcETL expert review, repeated pages", f"{args.runs or 'all'}-repeat",
                 [sheet(item) | {"id": repeats[item["id"]]} for item in sample])
    write_key(out, items, repeats)

    kinds = defaultdict(int)
    for item in items:
        kinds[(item["task"], "mapping" if item["review"].get("found") else "absence")] += 1
    occurrences = sum(len(item["occurrences"]) for item in items)
    print(f"{len(items)} distinct mappings from {occurrences} results, {len(sample)} repeated later:")
    for (task, kind), count in sorted(kinds.items()):
        print(f"  {task}, {kind}: {count}")
    print(f"For the expert: {out / 'for-expert'} (later: {out / 'for-expert-later'}); key, not for the expert: {out / 'key.csv'}")


if __name__ == "__main__":
    main()
