#!/usr/bin/env python3
"""Mean ± SD of the ETL agent evaluations over their runs, for the paper.

Reads the result files of run-us130-eval.sh and eval-dind (transformer-writer.json, harmonization-file.json,
gdc-benchmark.json, synthea-omop-benchmark.json) below a results folder, default results/eval, and groups the runs of one model and mode by the
name of the run without its number: run-1..3 are one setting, run-fix-1..3 and run-full-1 others.

    python3 local-learning-api/eval-summary.py [results/eval/<server run>] [--runs <name>] [--markdown summary.md]

US-130 and MIMIC-IV: source selection like GDC against the reference connectors of src/main/resources/connectors
(us-130-site-*.json, mimic-iv-demo-2.2.json, us-130-clinics.json), whose mapped columns are traced back through
their transformers. Effort and reuse per schema node come from the *-effort.json files of the runs.

GDC and Synthea-OMOP: recall, precision and F1 per schema field like gdc-benchmark-report.md, and like Magneto and MetaHarmonizer per source
column of the ground truth: Top-1 and Recall@GT, the fields the agent filled from a column counting as tied
(see GdcReport.columnScores). All values of a run are pooled over its datasets, GDC Top-1 and Recall@GT are the mean
of the studies like Table 1 of MetaHarmonizer.
"""
import argparse
import json
import re
import statistics
from collections import Counter, defaultdict
from pathlib import Path

RUN = re.compile(r"^run-(?:(?P<setting>.+)-)?(?P<number>\d+)$")
CONNECTORS = Path(__file__).parent / "src" / "main" / "resources" / "connectors"
REFERENCE_CONNECTORS = {**{f"US 130 Site {n}": f"us-130-site-{n:02d}.json" for n in range(2, 6)},
                        "MIMIC IV Demo": "mimic-iv-demo-2.2.json"}
CLINIC_CONNECTOR = "us-130-clinics.json"


# Inputs that join, order or parameterize, not columns holding the information of the node
NOT_INFORMATION = {"group", "target_group", "order", "anchor", "replacement", "pattern", "decimals", "divisor", "mode",
                   "tokens"}


def transformer_inputs(transformer):
    # The columns a transformer reads: its column list and the input values that name a column, not a parameter
    columns = {column.strip() for column in (transformer.get("column") or "").split(",") if column.strip()}
    columns |= {value for key, value in (transformer.get("inputMapping") or {}).items()
                if key not in NOT_INFORMATION and isinstance(value, str) and value
                and not value.startswith(("[VALUE]", "{", "["))}
    return columns


def reference_columns(file):
    """Per schema node path the source columns the reference connector maps it from, through its transformers."""
    path = CONNECTORS / file
    if not path.exists():
        return {}
    connector = json.loads(path.read_text())
    made_from = defaultdict(set)
    for transformer in connector.get("transformer") or []:
        for output in (transformer.get("returnMapping") or {}).values():
            made_from[output] |= transformer_inputs(transformer)
    reference = {}
    for mapping in connector.get("schemaMapping") or []:
        if mapping.get("column") and mapping.get("mapping"):
            open_columns, seen = [mapping["column"]], set()
            while open_columns:
                column = open_columns.pop()
                if column not in seen:
                    seen.add(column)
                    open_columns.extend(made_from.get(column, ()))
            reference[mapping["mapping"]] = sorted(column for column in seen if column not in made_from)
    return reference


def review_columns(review):
    # The source columns of a review, "name (TYPE, ...): values"
    return [column.split(" (")[0] for column in review.get("sourceColumns", [])] if review.get("found") else []


def matching_field(name, expected, review):
    mapped = review_columns(review)
    if not review.get("found"):
        verdict = "MISSED" if expected else "EMPTY"
    elif not expected:
        verdict = "EXTRA"
    else:
        verdict = "CORRECT" if set(mapped) & set(expected) else "WRONG_COLUMN"
    return {"field": name, "expected": expected, "mapped": mapped, "verdict": verdict}


def source_selection(fields_per_dataset):
    """Recall, precision and F1 like GDC for datasets with a reference connector, nothing without one."""
    if not any(fields_per_dataset.values()):
        return {}
    scores = gdc_benchmark(fields_per_dataset)
    return {f"source selection {metric}": scores[metric]
            for metric in ("recall", "precision", "F1", "Top-1 (per column)", "Recall@GT (per column)")}


def transformer_writer(data):
    verdicts = [node.get("verdict") for nodes in data.values() for node in nodes]
    judged = [verdict for verdict in verdicts if verdict]
    nodes = [node["review"] for nodes in data.values() for node in nodes]
    shifts = [node for node in nodes if node.get("truthShift") is not None]
    return {
        "datasets": len(data),
        "nodes": len(nodes),
        "mapped": share(sum(node["found"] for node in nodes), len(nodes)),
        "judged correct": share(sum(v["mappingCorrect"] and v["transformationCorrect"] for v in judged), len(judged)),
        "mapping correct": share(sum(v["mappingCorrect"] for v in judged), len(judged)),
        "transformation correct": share(sum(v["transformationCorrect"] for v in judged), len(judged)),
        **({"distribution shift (mean)": statistics.mean(node["truthShift"] for node in shifts)} if shifts else {}),
        **source_selection({dataset: [matching_field(node["review"]["schemaNode"],
                                                     reference_columns(REFERENCE_CONNECTORS[dataset]).get(node["review"]["schemaNode"], []),
                                                     node["review"]) for node in nodes]
                            for dataset, nodes in data.items() if dataset in REFERENCE_CONNECTORS}),
    }


def harmonization_file(data):
    columns = [column for dataset in data for column in dataset["columns"]]
    compared = [column for column in columns if column.get("truthShift") is not None]
    judged = [column["verdict"] for column in columns if column.get("verdict")]
    targets = sum(dataset["targets"] for dataset in data)
    return {
        "datasets": len(data),
        "columns": targets,
        "mapped": share(targets - sum(len(dataset["unmapped"]) for dataset in data), targets),
        "gold standard correct": share(sum(column["truthShift"] <= 0 for column in compared), len(compared)),
        **({"values equal to reference": statistics.mean(1 - column["truthShift"] for column in compared)} if compared else {}),
        "judged correct": share(sum(v["mappingCorrect"] and v["transformationCorrect"] for v in judged), len(judged)),
        "rejected values": sum(column.get("rejected", 0) for column in columns),
        **source_selection({dataset["dataset"]: harmonization_fields(dataset) for dataset in data
                            if dataset["dataset"] in REFERENCE_CONNECTORS}),
    }


def harmonization_fields(dataset):
    # The reference of a column of clinic 1 is the site column mapped onto the same schema node
    clinic = {node: columns for node, columns in reference_columns(CLINIC_CONNECTOR).items()}
    node_of = {column: node for node, columns in clinic.items() for column in columns}
    site = reference_columns(REFERENCE_CONNECTORS[dataset["dataset"]])
    return [matching_field(column["column"], site.get(node_of.get(column["column"]), []), column["review"] or {})
            for column in dataset["columns"]]


def effort(data):
    nodes = [node for nodes in data.values() for node in nodes]
    found = [node for node in nodes if node.get("source") != "not found"]
    sources = Counter(node.get("source") for node in found)
    return {
        "nodes": len(nodes),
        "runtime per node, s (median)": statistics.median(node["durationMs"] for node in nodes) / 1000 if nodes else 0,
        "model calls per node (mean)": statistics.mean(node["modelCalls"] for node in nodes) if nodes else 0,
        "validations per node (mean)": statistics.mean(node["validations"] for node in nodes) if nodes else 0,
        "questions": sum(node["questions"] for node in nodes),
        **{f"found from {source}": share(sources[source], len(found))
           for source in ("direct", "built-in", "store tool", "saved code", "new code")},
    }


def gdc_benchmark(data):
    fields = [field for study in data.values() for field in study]
    counts = defaultdict(int)
    for field in fields:
        counts[field["verdict"]] += 1
    truth = counts["CORRECT"] + counts["WRONG_COLUMN"] + counts["MISSED"]
    mapped = counts["CORRECT"] + counts["WRONG_COLUMN"] + counts["EXTRA"]
    recall = share(counts["CORRECT"], truth)
    precision = share(counts["CORRECT"], mapped)
    judged_right = sum(1 for field in fields if field.get("judged") is True)
    columns = [column_scores(study) for study in data.values()]
    return {
        "studies": len(data),
        "recall": recall,
        "precision": precision,
        "F1": 0 if recall + precision == 0 else 2 * recall * precision / (recall + precision),
        "judged precision": share(counts["CORRECT"] + judged_right, mapped),
        "extra": counts["EXTRA"],
        "Top-1 (per column)": statistics.mean(top1 for top1, _ in columns) if columns else 0,
        "Recall@GT (per column)": statistics.mean(recall for _, recall in columns) if columns else 0,
    }


def column_scores(fields):
    # Same as GdcReport.columnScores: the fields filled from a column are tied, as the agent ranks nothing
    targets, filled = defaultdict(set), defaultdict(set)
    for field in fields:
        for column in field["expected"]:
            targets[column].add(field["field"])
        for column in field["mapped"]:
            filled[column].add(field["field"])
    top1 = recall = 0.0
    for column, expected in targets.items():
        predicted = filled.get(column, set())
        right = len(predicted & expected)
        top1 += right / len(predicted) if predicted else 0
        recall += right / max(len(predicted), len(expected))
    return (top1 / len(targets), recall / len(targets)) if targets else (0, 0)


EVALUATIONS = {
    "transformer-writer.json": ("US-130 transformer writer", transformer_writer),
    "harmonization-file.json": ("US-130 file harmonization", harmonization_file),
    "gdc-benchmark.json": ("GDC-SM benchmark", gdc_benchmark),
    "synthea-omop-benchmark.json": ("Synthea-OMOP benchmark", gdc_benchmark),
    "transformer-writer-effort.json": ("Effort: US-130 and MIMIC-IV cohort import", effort),
    "harmonization-file-effort.json": ("Effort: US-130 file harmonization", effort),
    "gdc-benchmark-effort.json": ("Effort: GDC-SM benchmark", effort),
    "synthea-omop-benchmark-effort.json": ("Effort: Synthea-OMOP benchmark", effort),
}


def share(part, total):
    return part / total if total else 0


def collect(root, runs=None):
    groups = defaultdict(list)
    for file in sorted(root.rglob("*.json")):
        if file.name not in EVALUATIONS:
            continue
        match = RUN.match(file.parent.name)
        setting = match.group("setting") if match else file.parent.name
        if runs is not None and setting != runs:
            continue
        key = (EVALUATIONS[file.name][0], str(file.parent.parent.relative_to(root)), setting or "")
        groups[key].append((file.parent.name, EVALUATIONS[file.name][1](json.loads(file.read_text()))))
    return groups


def format_value(values):
    if all(isinstance(value, int) for value in values):
        return " / ".join(str(value) for value in values) if len(set(values)) > 1 else str(values[0])
    mean = statistics.mean(values)
    if len(values) == 1:
        return f"{mean:.3f}"
    return f"{mean:.3f} ± {statistics.stdev(values):.3f}"


def render(groups):
    lines = []
    for (evaluation, path, setting), runs in sorted(groups.items()):
        names = ", ".join(name for name, _ in runs)
        lines.append(f"## {evaluation}: {path}{' / ' + setting if setting else ''} (n={len(runs)}: {names})\n")
        lines.append("| Metric | Mean ± SD | Runs |")
        lines.append("|---|---|---|")
        for metric in dict.fromkeys(metric for _, scores in runs for metric in scores):
            values = [scores[metric] for _, scores in runs if metric in scores]
            per_run = " / ".join(str(value) if isinstance(value, int) else f"{value:.3f}" for value in values)
            lines.append(f"| {metric} | {format_value(values)} | {per_run} |")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", nargs="?", default=str(Path(__file__).parent / "results" / "eval"))
    parser.add_argument("--runs", help="only the runs of this name, e.g. protocol for run-protocol-1..3")
    parser.add_argument("--markdown", help="also write the summary to this file")
    args = parser.parse_args()
    text = render(collect(Path(args.root), args.runs))
    print(text)
    if args.markdown:
        Path(args.markdown).write_text(text)


if __name__ == "__main__":
    main()
