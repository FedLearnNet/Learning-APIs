"""
harmonization_lib — reversible site-heterogeneity operations for the FL-Net
data-harmonization evaluation (paper requirements RE1 / RE2).

Each :class:`Op` describes a *single* column-level transformation in BOTH
directions:

  * ``forward(value)``         — what a heterogeneous site does to a canonical
                                 value when it exports its idiosyncratic local
                                 data (run by the ``site_*.py`` scripts).
  * ``reverse_transformers()`` — the declarative FL-Net connector transformer(s)
                                 that undo it on import, expressed purely as
                                 configuration (a built-in function + parameters).

Because the forward operation and the connector transformer are generated from
the *same* object, the preprocessing scripts and the connectors are guaranteed
to be exact inverses by construction. The Java ``HarmonizationEvalTest`` then
verifies this empirically against the untouched canonical fixtures.

The built-in functions referenced here live in:
  local-learning-api/.../importer/functions/managed/builtin/
"""

from __future__ import annotations

import csv
import json
import os
from dataclasses import dataclass, field
from typing import Callable

HERE = os.path.dirname(os.path.abspath(__file__))
CONNECTORS_DIR = os.path.dirname(HERE)
BASELINE_CONNECTOR = os.path.join(CONNECTORS_DIR, "us-130-clinics.json")

# Canonical missing-value token used by the UCI US-130 dataset.
CANON_MISSING = "?"


# --------------------------------------------------------------------------- #
# Reversible operations
# --------------------------------------------------------------------------- #
class Op:
    """A reversible, column-scoped transformation."""

    def forward(self, value: str) -> str:  # pragma: no cover - interface
        raise NotImplementedError

    def reverse_transformers(self, columns: str) -> list[dict]:  # pragma: no cover
        raise NotImplementedError


def _transformer(method: str, column: str, input_mapping: dict) -> dict:
    """Assemble one declarative connector transformer entry."""
    return {
        "moduleName": "builtin",
        "methodName": method,
        "parameters": [],
        "returnKeys": [],
        "column": column,
        "onRow": False,
        "inputMapping": input_mapping,
        "returnMapping": {},
        "description": "",
        "appVersionId": None,
        "appImage": None,
        "hyperparams": {},
    }


@dataclass
class ValueMap(Op):
    """Recode categorical values through a site-specific coding system.

    ``forward_map`` maps canonical -> site code. The reverse connector uses the
    built-in ``Generic Value Mapper`` with the inverted map (site -> canonical).

    ``missing_code`` (optional) is the site code emitted for the canonical
    missing token ``?``; on import it is mapped back to empty so the importer
    treats the cell as absent (matching the reference site's null handling).
    """

    forward_map: dict[str, str]
    missing_code: str | None = None

    def forward(self, value: str) -> str:
        if self.missing_code is not None and value in (None, "", CANON_MISSING):
            return self.missing_code
        return self.forward_map.get(value, value)

    def reverse_transformers(self, columns: str) -> list[dict]:
        reverse_map = {v: k for k, v in self.forward_map.items()}
        if self.missing_code is not None:
            reverse_map[self.missing_code] = ""
        return [_transformer("Generic Value Mapper", columns,
                             {"map": json.dumps(reverse_map, ensure_ascii=False)})]


@dataclass
class Scale(Op):
    """Change the unit of a numeric variable by a constant factor.

    forward: canonical * factor (e.g. days -> hours, factor=24).
    reverse: built-in ``Scale Numeric`` divides by the same factor.
    """

    factor: int

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return value
        return str(int(value) * self.factor)

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Scale Numeric", columns,
                             {"divisor": str(self.factor), "decimals": "0"})]


@dataclass
class IsoDuration(Op):
    """Represent an integer number of days as an ISO-8601 duration.

    forward: 3 -> "P3D".  reverse: built-in ``Duration To Days``.
    """

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return value
        return f"P{int(value)}D"

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Duration To Days", columns, {})]


@dataclass
class CaseMangle(Op):
    """Mangle casing and surrounding whitespace of a Title-cased token.

    forward: lower-cases and pads with whitespace (e.g. "Steady" -> "  steady ").
    reverse: built-in ``Trim And Case`` with mode=TITLE restores the canonical
    Title-case form.
    """

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return value
        return f"  {value.lower()} "

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Trim And Case", columns, {"mode": "TITLE"})]


@dataclass
class Pad(Op):
    """Surround a value with whitespace without touching its casing.

    forward: "AfricanAmerican" -> " AfricanAmerican ".
    reverse: built-in ``Trim And Case`` with mode=NONE (trim only).
    """

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return value
        return f" {value} "

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Trim And Case", columns, {"mode": "NONE"})]


@dataclass
class InnerSpaces(Op):
    """Insert cosmetic spaces inside compact tokens (e.g. "<30" -> "< 30 ").

    reverse: built-in ``Regex Replace`` strips all whitespace.
    """

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return value
        # space out a leading comparator and pad
        spaced = value.replace("<", "< ").replace(">", "> ")
        return f" {spaced} "

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Regex Replace", columns,
                             {"pattern": r"\s+", "replacement": ""})]


@dataclass
class PadWithMissing(Op):
    """Pad real values with whitespace and replace the canonical missing token
    with a (padded) site sentinel.

    forward: "AfricanAmerican" -> " AfricanAmerican "; "?" -> " NULL ".
    reverse: ``Normalize Missing`` (sentinels -> empty) THEN ``Trim And Case``
    mode=NONE (trim the surviving real values). The two transformers are emitted
    in that order so the round-trip is exact.
    """

    tokens: list[str]

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return f" {self.tokens[0]} "
        return f" {value} "

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [
            _transformer("Normalize Missing", columns,
                         {"tokens": json.dumps(self.tokens + [CANON_MISSING], ensure_ascii=False),
                          "replacement": ""}),
            _transformer("Trim And Case", columns, {"mode": "NONE"}),
        ]


@dataclass
class Sentinel(Op):
    """Replace the canonical missing token with a site-specific sentinel.

    forward: "?" -> sentinel (the first token); real values pass through.
    reverse: built-in ``Normalize Missing`` maps every sentinel back to empty,
    which the importer treats as an absent value for non-nullable nodes.
    """

    tokens: list[str]

    def forward(self, value: str) -> str:
        if value is None or value == "" or value == CANON_MISSING:
            return self.tokens[0]
        return value

    def reverse_transformers(self, columns: str) -> list[dict]:
        return [_transformer("Normalize Missing", columns,
                             {"tokens": json.dumps(self.tokens, ensure_ascii=False),
                              "replacement": ""})]


# --------------------------------------------------------------------------- #
# Row-level (multi-column) reversible operations
# --------------------------------------------------------------------------- #
# Cell ops above act on one column at a time. The ops below restructure the row
# itself — combining several columns into one or splitting one into several — and
# are reversed by *row* built-ins (``Split Column`` / ``Merge Columns``) that read
# and write whole rows. Together the two families let a reader see both the
# cell-based and the row-based connector function model.
class RowOp:
    """A reversible transformation over multiple columns of a row."""

    def forward_apply(self, row: dict) -> None:  # pragma: no cover - interface
        raise NotImplementedError

    def header_transform(self, header: list[str]) -> list[str]:  # pragma: no cover
        raise NotImplementedError

    def reverse_transformer(self) -> dict:  # pragma: no cover
        raise NotImplementedError


def _row_transformer(method: str, column: str, input_mapping: dict,
                     return_mapping: dict, hyperparams: dict) -> dict:
    return {
        "moduleName": "builtin",
        "methodName": method,
        "parameters": [],
        "returnKeys": list(return_mapping.keys()),
        "column": column,
        "onRow": True,
        "inputMapping": input_mapping,
        "returnMapping": return_mapping,
        "description": "",
        "appVersionId": None,
        "appImage": None,
        "hyperparams": hyperparams,
    }


@dataclass
class CombineColumns(RowOp):
    """Concatenate several columns into one packed column.

    forward: drop ``sources`` and emit ``target`` = sources joined by ``delimiter``
             (e.g. diag_1/diag_2/diag_3 -> "250.83|276|255").
    reverse: the ``Split Column`` row built-in splits ``target`` back into the
             original columns.
    """

    sources: list[str]
    target: str
    delimiter: str = "|"

    def forward_apply(self, row: dict) -> None:
        packed = self.delimiter.join(row[s] for s in self.sources)
        for s in self.sources:
            del row[s]
        row[self.target] = packed

    def header_transform(self, header: list[str]) -> list[str]:
        out, inserted = [], False
        for col in header:
            if col in self.sources:
                if not inserted:
                    out.append(self.target)
                    inserted = True
                continue
            out.append(col)
        return out

    def reverse_transformer(self) -> dict:
        return _row_transformer(
            "Split Column", self.target,
            input_mapping={"source": self.target},
            return_mapping={s: s for s in self.sources},
            hyperparams={"delimiter": self.delimiter,
                         "parts": json.dumps(self.sources, ensure_ascii=False)},
        )


@dataclass
class SplitColumn(RowOp):
    """Split one column into several by a delimiter.

    forward: drop ``source`` and emit ``targets`` from source.split(delimiter)
             (e.g. age "[50-60)" -> "[50" , "60)").
    reverse: the ``Merge Columns`` row built-in joins ``targets`` back into
             ``source``.
    """

    source: str
    targets: list[str]
    delimiter: str = "-"

    def forward_apply(self, row: dict) -> None:
        pieces = row[self.source].split(self.delimiter)
        for i, t in enumerate(self.targets):
            row[t] = pieces[i] if i < len(pieces) else ""
        del row[self.source]

    def header_transform(self, header: list[str]) -> list[str]:
        out, inserted = [], False
        for col in header:
            if col == self.source:
                if not inserted:
                    out.extend(self.targets)
                    inserted = True
                continue
            out.append(col)
        return out

    def reverse_transformer(self) -> dict:
        return _row_transformer(
            "Merge Columns", self.source,
            input_mapping={t: t for t in self.targets},
            return_mapping={"value": self.source},
            hyperparams={"delimiter": self.delimiter,
                         "parts": json.dumps(self.targets, ensure_ascii=False)},
        )


# --------------------------------------------------------------------------- #
# Site specification
# --------------------------------------------------------------------------- #
@dataclass
class ColumnPlan:
    """How one canonical column is renamed and transformed for a site."""

    canonical: str
    site_name: str
    ops: list[Op] = field(default_factory=list)


@dataclass
class SiteSpec:
    site_id: int
    title: str
    delimiter: str
    columns: list[ColumnPlan]
    # Row-level restructuring (combine/split) reversed by row built-ins.
    row_ops: list[RowOp] = field(default_factory=list)
    # If set, the output columns are deterministically permuted to also exercise
    # positional heterogeneity (column *order* differs between sites). FL-Net maps
    # by name, so the connector needs no extra configuration for this.
    shuffle_seed: int | None = None

    def site_csv_name(self) -> str:
        return f"site_{self.site_id:02d}.csv"

    def connector_name(self) -> str:
        return f"us-130-site-{self.site_id:02d}.json"


# --------------------------------------------------------------------------- #
# Forward (preprocessing) and reverse (connector) generation
# --------------------------------------------------------------------------- #
def _read_canonical(canonical_csv: str) -> tuple[list[str], list[dict]]:
    with open(canonical_csv, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        rows = list(reader)
        header = reader.fieldnames or []
    return header, rows


def preprocess(spec: SiteSpec, canonical_csv: str, out_csv: str) -> dict:
    """Apply the forward (heterogenising) transform; write the site export CSV.

    Returns a small manifest describing what was changed (used for reporting).
    """
    header, rows = _read_canonical(canonical_csv)
    plan_by_canon = {c.canonical: c for c in spec.columns}

    out_header = []
    for col in header:
        plan = plan_by_canon.get(col)
        out_header.append(plan.site_name if plan else col)

    for row_op in spec.row_ops:
        out_header = row_op.header_transform(out_header)

    if spec.shuffle_seed is not None:
        import random
        rng = random.Random(spec.shuffle_seed)
        # Keep the external identifier columns first for readability, shuffle the rest.
        fixed = out_header[:2]
        rest = out_header[2:]
        rng.shuffle(rest)
        out_header = fixed + rest

    out_rows = []
    coercions = 0
    for row in rows:
        out = {}
        for col in header:
            plan = plan_by_canon.get(col)
            value = row[col]
            if plan is None:
                out[col] = value
                continue
            new_value = value
            for op in plan.ops:
                new_value = op.forward(new_value)
            if new_value != value:
                coercions += 1
            out[plan.site_name] = new_value
        for row_op in spec.row_ops:
            row_op.forward_apply(out)
        out_rows.append(out)

    with open(out_csv, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=out_header, delimiter=spec.delimiter)
        writer.writeheader()
        writer.writerows(out_rows)

    return {
        "site_id": spec.site_id,
        "title": spec.title,
        "rows": len(out_rows),
        "renamed_columns": sum(1 for c in spec.columns if c.site_name != c.canonical),
        "transformed_columns": sum(1 for c in spec.columns if c.ops),
        "cell_coercions": coercions,
        "delimiter": spec.delimiter,
        "output": os.path.basename(out_csv),
    }


def _chunk_columns(columns: list[str], max_len: int) -> list[list[str]]:
    """Split a column list so that each comma-joined chunk stays within max_len."""
    chunks: list[list[str]] = []
    current: list[str] = []
    for col in columns:
        candidate = current + [col]
        if current and len(",".join(candidate)) > max_len:
            chunks.append(current)
            current = [col]
        else:
            current = candidate
    if current:
        chunks.append(current)
    return chunks


def _load_baseline_schema_mapping() -> tuple[list[dict], dict]:
    with open(BASELINE_CONNECTOR, encoding="utf-8") as fh:
        baseline = json.load(fh)
    return baseline["schemaMapping"], baseline["inputConfig"]


def build_connector(spec: SiteSpec) -> dict:
    """Produce the declarative reverse connector for a site.

    The schema mapping is the shared one (same ``mapping``/``schemaId`` as the
    reference site); only the source ``column`` names are swapped to the site's
    renamed columns. The transformer list undoes the value-level heterogeneity
    before mapping, so every site lands in the identical shared representation.
    """
    schema_mapping, input_config = _load_baseline_schema_mapping()
    rename = {c.canonical: c.site_name for c in spec.columns}

    new_mapping = []
    for entry in schema_mapping:
        entry = dict(entry)
        entry["column"] = rename.get(entry["column"], entry["column"])
        new_mapping.append(entry)

    # Group columns that share the *same* reverse op into one transformer scope
    # (a comma-separated column list), exactly as a human author would write it
    # for the 23 medication columns. Each column carries a single op, so there is
    # no cross-op ordering to worry about; an op may still emit several ordered
    # transformers (e.g. normalize-missing then trim), which we keep adjacent.
    transformers: list[dict] = []
    grouped: dict[str, list[str]] = {}
    order: list[tuple[str, Op]] = []
    for plan in spec.columns:
        for op in plan.ops:
            signature = json.dumps(op.reverse_transformers("§"), sort_keys=True)
            if signature not in grouped:
                grouped[signature] = []
                order.append((signature, op))
            grouped[signature].append(plan.site_name)
    # The connector_transformers.column_name column is varchar(255), so a single
    # transformer can only name so many columns. Chunk wide groups (e.g. the 23
    # medication columns) into several transformers that each stay within the limit.
    max_column_len = 240
    for signature, op in order:
        for chunk in _chunk_columns(grouped[signature], max_column_len):
            transformers.extend(op.reverse_transformers(",".join(chunk)))

    # Row-level reverse transformers (split/merge) run alongside the cell ones;
    # they operate on disjoint columns, so ordering relative to the cell ops is
    # irrelevant. They reconstruct the canonical columns the schema mapping reads.
    for row_op in spec.row_ops:
        transformers.append(row_op.reverse_transformer())

    input_config = dict(input_config)
    input_config["delimiter"] = spec.delimiter

    return {
        "id": 300 + spec.site_id,
        "version": 1,
        "createdAt": "2026-06-18T00:00:00Z",
        "updatedAt": "2026-06-18T00:00:00Z",
        "name": f"US130-Site-{spec.site_id:02d}",
        "description": spec.title,
        "inputConfig": input_config,
        "transformer": transformers,
        "schemaMapping": new_mapping,
        "mergeConfig": None,
    }


def emit(spec: SiteSpec) -> dict:
    """Run the full pipeline for a site: write the heterogeneous CSV and the
    reverse connector next to the canonical fixtures. Returns the manifest."""
    canonical_csv = os.path.join(CONNECTORS_DIR, f"clinic_{spec.site_id:03d}.csv")
    out_csv = os.path.join(CONNECTORS_DIR, spec.site_csv_name())
    manifest = preprocess(spec, canonical_csv, out_csv)

    connector = build_connector(spec)
    out_connector = os.path.join(CONNECTORS_DIR, spec.connector_name())
    with open(out_connector, "w", encoding="utf-8") as fh:
        json.dump(connector, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    manifest["connector"] = spec.connector_name()
    manifest["reverse_transformers"] = len(connector["transformer"])
    return manifest


# --------------------------------------------------------------------------- #
# Canonical column groups shared across sites
# --------------------------------------------------------------------------- #
MEDICATION_COLUMNS = [
    "metformin", "repaglinide", "nateglinide", "chlorpropamide", "glimepiride",
    "acetohexamide", "glipizide", "glyburide", "tolbutamide", "pioglitazone",
    "rosiglitazone", "acarbose", "miglitol", "troglitazone", "tolazamide",
    "examide", "citoglipton", "insulin", "glyburide-metformin",
    "glipizide-metformin", "glimepiride-pioglitazone", "metformin-rosiglitazone",
    "metformin-pioglitazone",
]
