#!/usr/bin/env python3
"""Mean ± SD of the ETL agent evaluations over their runs, for the paper.

Reads the result files of run-us130-eval.sh and eval-dind (transformer-writer.json, harmonization-file.json,
gdc-benchmark.json) below a results folder, default results/eval, and groups the runs of one model and mode by the
name of the run without its number: run-1..3 are one setting, run-fix-1..3 and run-full-1 others.

    python3 local-learning-api/eval-summary.py [results/eval/<server run>] [--markdown summary.md]

GDC: recall, precision and F1 per GDC field like gdc-benchmark-report.md, and like Magneto and MetaHarmonizer per source
column of the ground truth: Top-1 and Recall@GT, the fields the agent filled from a column counting as tied
(see GdcReport.columnScores). All values of a run are pooled over its datasets, GDC Top-1 and Recall@GT are the mean
of the studies like Table 1 of MetaHarmonizer.
"""
import argparse
import json
import re
import statistics
from collections import defaultdict
from pathlib import Path

RUN = re.compile(r"^run-(?:(?P<setting>.+)-)?(?P<number>\d+)$")


def transformer_writer(data):
    verdicts = [node.get("verdict") for nodes in data.values() for node in nodes]
    judged = [verdict for verdict in verdicts if verdict]
    nodes = [node["review"] for nodes in data.values() for node in nodes]
    return {
        "datasets": len(data),
        "nodes": len(nodes),
        "mapped": share(sum(node["found"] for node in nodes), len(nodes)),
        "judged correct": share(sum(v["mappingCorrect"] and v["transformationCorrect"] for v in judged), len(judged)),
        "mapping correct": share(sum(v["mappingCorrect"] for v in judged), len(judged)),
        "transformation correct": share(sum(v["transformationCorrect"] for v in judged), len(judged)),
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
        "judged correct": share(sum(v["mappingCorrect"] and v["transformationCorrect"] for v in judged), len(judged)),
        "rejected values": sum(column.get("rejected", 0) for column in columns),
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
}


def share(part, total):
    return part / total if total else 0


def collect(root):
    groups = defaultdict(list)
    for file in sorted(root.rglob("*.json")):
        if file.name not in EVALUATIONS:
            continue
        match = RUN.match(file.parent.name)
        setting = match.group("setting") if match else file.parent.name
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
        for metric in runs[0][1]:
            values = [scores[metric] for _, scores in runs]
            per_run = " / ".join(str(value) if isinstance(value, int) else f"{value:.3f}" for value in values)
            lines.append(f"| {metric} | {format_value(values)} | {per_run} |")
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", nargs="?", default=str(Path(__file__).parent / "results" / "eval"))
    parser.add_argument("--markdown", help="also write the summary to this file")
    args = parser.parse_args()
    text = render(collect(Path(args.root)))
    print(text)
    if args.markdown:
        Path(args.markdown).write_text(text)


if __name__ == "__main__":
    main()
