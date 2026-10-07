# Transformer writer evaluation (gpt-oss:latest)

Every dataset is imported completely with the connector the agent wrote. Mapping / transformation correct by the LLM judge: ✓ / ✗, - not judged.

## us130-site-2 - Numeric coding systems (HL7 gender, race codebook, ordinal codes)

Import: FINISHED, 779 patients, 775 data entries, 0 failed

| Schema node | Source columns | Status | Mode | Checks | Sample valid | Imported | Rejected | LLM judge | Time (s) |
|---|---|---|---|---|---|---|---|---|---|
| Demographics.Gender | sex | WRITTEN | CELL | 1/2 | 200/200 | 775 (0 empty) | 0 | ✓ / ✓ | 63 |
| Lab Results.A1C Result | hba1c_class | WRITER_FAILED | - | 0/32 | 0/0 | - | - | ✓ / ✗ | 286 |

## us130-site-3 - Semicolon-delimited German export, length-of-stay in hours

Import: FINISHED, 777 patients, 771 data entries, 0 failed

| Schema node | Source columns | Status | Mode | Checks | Sample valid | Imported | Rejected | LLM judge | Time (s) |
|---|---|---|---|---|---|---|---|---|---|
| Encounter.Time in Hospital | verweildauer_stunden | WRITTEN | CELL | 1/26 | 200/200 | 771 (0 empty) | 0 | ✓ / ✓ | 202 |
| Demographics.Age | alter_von, alter_bis | WRITER_FAILED | - | 0/39 | 0/0 | - | - | ✓ / ✗ | 235 |
| Outcome.Readmitted | wiederaufnahme | WRITER_FAILED | - | 0/35 | 0/0 | - | - | ✓ / ✗ | 198 |

## us130-site-4 - ISO-8601 duration, symbolic dosage codes, bracket-free ages

Import: FINISHED, 776 patients, 770 data entries, 0 failed

| Schema node | Source columns | Status | Mode | Checks | Sample valid | Imported | Rejected | LLM judge | Time (s) |
|---|---|---|---|---|---|---|---|---|---|
| Encounter.Time in Hospital | StayDuration | WRITTEN | CELL | 1/18 | 200/200 | 770 (0 empty) | 0 | ✓ / ✓ | 1059 |
| Demographics.Age | AgeRange | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 13 |
| Medication.Metformin | Rx_metformin | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 2 |
| Demographics.Race | RaceCode | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 13 |

## us130-site-5 - Messy casing/whitespace and heterogeneous missing sentinels

Import: FINISHED, 780 patients, 0 data entries, 0 failed

| Schema node | Source columns | Status | Mode | Checks | Sample valid | Imported | Rejected | LLM judge | Time (s) |
|---|---|---|---|---|---|---|---|---|---|
| Demographics.Race | race | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 17 |
| Outcome.Readmitted | readmitted | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 15 |
| Demographics.Age | age_low, age_high | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✓ / ✗ | 14 |

## mimic-iv-demo - MIMIC-IV v2.2 demo, relational tables merged by subject_id

Import: RUNNING, 2 patients, 0 data entries, 0 failed

| Schema node | Source columns | Status | Mode | Checks | Sample valid | Imported | Rejected | LLM judge | Time (s) |
|---|---|---|---|---|---|---|---|---|---|
| Demographics.Gender | gender | WRITTEN | CELL | 1/9 | 100/100 | 2 (0 empty) | 0 | ✓ / ✓ | 134 |
| Demographics.Race | race | WRITTEN | CELL | 1/24 | 200/200 | 0 (0 empty) | 0 | ✓ / ✓ | 180 |
| Demographics.Age | anchor_age | WRITTEN | CELL | 1/13 | 100/100 | 2 (0 empty) | 0 | ✓ / ✓ | 181 |
| Encounter.Time in Hospital | admittime, dischtime | WRITTEN | ROW | 1/6 | 200/200 | 2 (0 empty) | 0 | ✓ / ✓ | 97 |
| Outcome.Readmitted | admittime, dischtime | NOT_FOUND | - | 0/0 | 0/0 | - | - | ✗ / ✗ | 3 |

## Summary

- 17 mappings: 7 written, 7 not found, 3 writer failures, 0 evaluation errors, 5 with import problems
- LLM judge: 17 judged, correct 7 (41%), mapping correct 16, transformation correct 7

## Mapping errors

### us130-site-2 / Lab Results.A1C Result - WRITER_FAILED
- Evaluation error: RuntimeException: Something is wrong, exceeded 10 sequential tool executions
- LLM judge ✓ / ✗: The source column 'hba1c_class' contains numeric codes (0,1,2,3) that can be mapped to the allowed categorical values of Lab Results.A1C Result (>8, >7, None, Norm) as confirmed by the user answers (0 → None, 1 → Norm, 2 → >7, 3 → >8). Thus the source column does hold the necessary information. However, the agent failed to create a transformation (the writer crashed with a RuntimeException) and the schema node was not included in the connector import. Therefore, no transformation was performed, so we cannot confirm that source values are correctly converted into the semantically right values of the schema node.

### us130-site-3 / Demographics.Age - WRITER_FAILED
- Evaluation error: RuntimeException: Something is wrong, exceeded 10 sequential tool executions
- LLM judge ✓ / ✗: The two source columns `alter_von` and `alter_bis` together encode the 10‑year age interval (e.g., `[20` and `30)` represent the interval [20‑30)). Therefore the source data contain the necessary information for the Demographics.Age node. However, the agent did not generate a transformation (the writer crashed), and the node was not included in the connector import. Consequently we cannot confirm that the source values are converted into the allowed categorical values (e.g., `[20-30)`), so the transformation is considered incorrect.

### us130-site-3 / Outcome.Readmitted - WRITER_FAILED
- Evaluation error: RuntimeException: Something is wrong, exceeded 10 sequential tool executions
- LLM judge ✓ / ✗: The source column "wiederaufnahme" contains the readmission status in natural language ('Keine', 'Binnen 30 Tagen', 'Nach 30 Tagen'), which directly corresponds to the schema values NO, <30, and >30. Thus the column holds the necessary information. However, the agent did not create any transformation and the schema node was not included in the connector import, so we cannot confirm that the source values are converted into the semantically correct schema values.

### us130-site-4 / Demographics.Age - NOT_FOUND
- Writer gave up: The last answer is invalid: Running the transformer failed: Function not found: custom.code.python/transform
- LLM judge ✓ / ✗: The source column "AgeRange" contains 10‑year age intervals (e.g., '30-39', '70-79', '80-89', etc.) that represent the same age groups as the schema’s allowed categorical values (e.g., [30‑40), [70‑80), [80‑90), etc.). Thus the source does hold the required information. However, the agent failed to create a transformer (the custom code lookup failed), and the node was not imported into the connector. Consequently we cannot confirm that the source values are converted into the exact allowed categorical strings; a transformation would be needed to map '30-39' → '[30-40)', '70-79' → '[70-80)', etc.

### us130-site-4 / Medication.Metformin - NOT_FOUND
- Writer gave up: Ask the user to clarify the meaning of the values in the Rx_metformin column (e.g. whether "-" means Down, No change, or another state) before implementing a custom transformer.
- LLM judge ✓ / ✗: The source column `Rx_metformin` contains four distinct symbols that can be mapped to the four expected categories: '-' → Down, '+' → Up, '=' → Steady, and 'x' → No. Thus the source does hold the necessary information for the Medication.Metformin node. However, the agent did not provide a transformer and the node was not included in the import, so we cannot confirm that the source values are converted into the correct categorical values. Hence transformationCorrect is false.

### us130-site-4 / Demographics.Race - NOT_FOUND
- Writer gave up: The last answer is invalid: Running the transformer failed: Function not found: custom.code.python/transform
- LLM judge ✓ / ✗: The source column `RaceCode` contains codes that directly map onto the allowed race categories: CAUC → Caucasian, AFAM → AfricanAmerican, HISP → Hispanic, ASIA → Asian, and OTHR → Other. Thus the source does hold the necessary information for the Demographics.Race node. However, the agent did not provide a transformer (the custom code lookup failed) and the node was not imported, so we cannot confirm that each source value is converted into the correct categorical value. In particular, the code `UNK` (unknown) is not handled; a proper transformation would need to map it to 'Other' or reject it, but this was not implemented. Therefore transformationCorrect is false.

### us130-site-5 / Demographics.Race - NOT_FOUND
- Writer gave up: The last answer is invalid: 2 rejected values of 'race':
' Caucasian ': Value  Caucasian  is not allowed for data type Race or Ethnicity custom categories
' AfricanAmerican ': Value  AfricanAmerican  is not allowed for data type Race or Ethnicity custom categories
- LLM judge ✓ / ✗: The source column `race` contains the race names that correspond directly to the allowed values of the schema node, albeit with leading and trailing spaces (e.g., `' Caucasian '` should map to `Caucasian`). Therefore the source does hold the required information. However, the agent did not provide a transformation to trim or otherwise clean these strings, and the import rejected the values due to the spaces, resulting in no data for this node. Consequently the transformation is not correct and no mapping was imported.

### us130-site-5 / Outcome.Readmitted - NOT_FOUND
- Writer gave up: The last answer is invalid: Running the transformer failed: Function not found: custom.code.python/transform
- LLM judge ✓ / ✗: The source column `readmitted` contains the three expected categories, albeit with surrounding spaces: `' NO '`, `' > 30 '`, `' < 30 '`. These directly correspond to the allowed values `[<30, >30, NO]`. Thus the source does hold the required information for the Outcome.Readmitted node. However, no transformer was created (the custom code lookup failed) and the connector rejected the values because of the leading/trailing spaces, resulting in zero data entries for this node. Consequently the transformation is not correct.

### us130-site-5 / Demographics.Age - NOT_FOUND
- Writer gave up: The last answer is invalid: Running the transformer failed: Function not found: custom.code.python/transform
- LLM judge ✓ / ✗: The source columns `age_low` and `age_high` contain the lower and upper bounds that can be combined to form the 10‑year interval strings required by Demographics.Age (e.g., `age_low='[40'` and `age_high='50)'` should produce `[40-50)`). Therefore the source does hold the necessary information. However, the agent did not provide any transformation logic, the custom code lookup failed, and the connector imported zero data entries for this node. Consequently we cannot confirm that the source values are correctly turned into the allowed categorical values, and the transformation is therefore considered incorrect.

### mimic-iv-demo / Demographics.Gender - WRITTEN
- Import: import run RUNNING
- LLM judge ✓ / ✓: The source column `gender` contains the single‑letter codes 'F' and 'M', which directly represent the allowed values Female and Male. The custom transformer `MapGenderCode` correctly normalises these codes to the full categorical values, handling case and whitespace. The sample results and the full import show that all values were converted to either 'Female' or 'Male' with no empty or rejected entries, confirming both mapping and transformation are correct.
- Imported values: {'Female'=1, 'Male'=1}
- Transformation: Custom code transformer 'MapGenderCode' (CELL): Maps single-letter gender codes to full categorical values.; column gender
```python
if value is None:
    return None
if isinstance(value, str):
    v = value.strip().upper()
    if v == 'F':
        return 'Female'
    if v == 'M':
        return 'Male'
return None
```

### mimic-iv-demo / Demographics.Race - WRITTEN
- Import: import run RUNNING
- Import: no data entry imported
- LLM judge ✓ / ✓: The source column `race` contains free‑text labels that fully capture the race information needed by the Demographics.Race node. The custom transformer `RaceMapper` correctly normalises these labels to the allowed categories (Caucasian, Asian, Hispanic, AfricanAmerican, Other) and returns 'Other' for unrecognised or missing values. The sample mapping results and the examples demonstrate accurate conversion. The import issue (zero data entries) is caused by duplicate race entries per patient, which violates the atomic attribute rule, not by incorrect mapping or transformation logic.
- Imported values: {}
- Transformation: Custom code transformer 'RaceMapper' (CELL): Maps free-text race labels to the standard Race or Ethnicity categories.
Handles common variations such as "BLACK/AFRICAN AMERICAN" or "HISPANIC/LATINO – PUERTO RICAN".
Returns "Other" for unknown or unrecognised values.; column race
```python
    if value is None or not isinstance(value, str):
        return 'Other'
    v = value.strip().lower()
    if 'unknown' in v:
        return 'Other'
    if 'white' in v:
        return 'Caucasian'
    if 'black' in v or 'african' in v or 'american' in v:
        return 'AfricanAmerican'
    if 'hispanic' in v or 'latino' in v:
        return 'Hispanic'
    if 'asian' in v:
        return 'Asian'
    if 'other' in v:
        return 'Other'
    return 'Other'

```

### mimic-iv-demo / Demographics.Age - WRITTEN
- Import: import run RUNNING
- LLM judge ✓ / ✓: The source column `anchor_age` contains numeric age values that directly encode the information needed for the Demographics.Age node. The provided transformer `AgeBracketMapper` correctly converts each age to its 10‑year bracket (e.g., 21 → "[20-30)", 72 → "[70-80)") and rejects values outside 0–99 by returning None, which is appropriate given the allowed ranges. The sample mapping and the full import (two patients correctly assigned to brackets) confirm that every source value is transformed into a semantically valid category from the allowed list.
- Imported values: {'[60-70)'=1, '[80-90)'=1}
- Transformation: Custom code transformer 'AgeBracketMapper' (CELL): Maps numeric age to 10-year bracket categories (e.g., 21 -> [20-30)). Handles missing or invalid values by returning None.; column anchor_age
```python
if value is None:
    return None
try:
    age = int(value)
except Exception:
    return None
if age < 0 or age >= 100:
    return None
start = (age // 10) * 10
end = start + 10
return f'[{start}-{end})'

```

### mimic-iv-demo / Encounter.Time in Hospital - WRITTEN
- Import: import run RUNNING
- LLM judge ✓ / ✓: The source columns `admittime` and `dischtime` provide the necessary admission and discharge timestamps that uniquely determine the length of stay. The custom transformer `HospitalStayDays` parses the timestamps, computes the difference in days using `delta.days`, and returns the integer value (or None for invalid/negative intervals). The sample results and the import data confirm that all valid rows are converted to correct integer day counts, satisfying the schema requirement.
- Imported values: {'4'=1, '6'=1}
- Transformation: Custom code transformer 'HospitalStayDays' (ROW): Calculates the number of days between admission and discharge dates.; input {admittime=admittime, dischtime=dischtime}; output {value=Time in Hospital}
```python
admittime = row.get('admittime')
dischtime = row.get('dischtime')
if not admittime or not dischtime:
    return None
try:
    fmt = '%Y-%m-%d %H:%M:%S'
    from datetime import datetime
    at = datetime.strptime(admittime, fmt)
    dt = datetime.strptime(dischtime, fmt)
    delta = dt - at
    days = delta.days
    return days if days >= 0 else None
except Exception:
    return None

```

### mimic-iv-demo / Outcome.Readmitted - NOT_FOUND
- Writer gave up: To determine whether a patient was readmitted (NO, <30, >30), you need data on subsequent admissions for the same patient after the discharge date. The current dataset only contains a single admission record per patient, so the readmission status cannot be computed from the provided columns. Please provide the admission dates of subsequent hospital stays (e.g., a table with all admissions per patient) or an indicator that specifies whether a readmission occurred within 30 days.
- Import: import run RUNNING
- LLM judge ✗ / ✗: The source columns `admittime` and `dischtime` only describe a single admission episode. They do not contain any information about subsequent admissions or the time between discharge and a potential readmission, which is required to classify the outcome as `<30`, `>30`, or `NO`. Because the necessary data is absent, the schema node cannot be mapped or transformed, so both mappingCorrect and transformationCorrect are false.
