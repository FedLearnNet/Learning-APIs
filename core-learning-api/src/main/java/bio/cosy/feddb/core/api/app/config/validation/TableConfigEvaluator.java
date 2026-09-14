package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.*;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TableConfigEvaluator implements ToolConfigEvaluator {
    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> meta = new LinkedHashMap<>();

        if (config == null) {
            return fail(errors, meta, "config is null");
        }
        if (file == null) {
            return fail(errors, meta, "file is null");
        }
        if (profile == null) {
            return fail(errors, meta, "profile is null (table must be profiled before evaluation)");
        }

        // Type guard
        ToolConfigDataType type = config.getType();
        if (type != ToolConfigDataType.CSV && type != ToolConfigDataType.TSV) {
            return fail(errors, meta, "TableConfigEvaluator supports only CSV/TSV but got: " + type);
        }

        // Meta
        meta.put("fileName", file.getFileName());
        meta.put("contentType", file.getContentType());
        meta.put("size", file.getSize());
        meta.put("rowsScanned", profile.getRowsScanned());
        meta.put("columnCount", profile.getColumns() == null ? 0 : profile.getColumns().size());

        long rows = profile.getRowsScanned();
        int cols = profile.getColumns() == null ? 0 : profile.getColumns().size();

        // If profile is empty -> fail early
        if (rows <= 0) {
            return fail(errors, meta, "Table is empty (rowsScanned=0).");
        }
        if (cols <= 0) {
            return fail(errors, meta, "Table has no columns (columns empty).");
        }

        // Build lookup
        Map<String, ColumnProfile> byName = (profile.getColumns() == null ? List.<ColumnProfile>of() : profile.getColumns())
                .stream()
                .filter(c -> c != null && c.name() != null && !c.name().isBlank())
                .collect(Collectors.toMap(ColumnProfile::name, c -> c, (a, b) -> a, LinkedHashMap::new));

        // Evaluate schema if present
        TabularSchemaDTO schema = config.getTabularSchema();
        if (schema != null) {
            validateBounds(schema, rows, cols, errors);
            validateRequiredColumns(schema, byName, errors);

            // allowNulls=false => no missing anywhere
            validateAllowNulls(schema, profile, errors);

            // allowOnlyNumbers=true => every column must be numeric-ish
            validateAllowOnlyNumbers(schema, profile, errors);

            // per-column rules
            validateColumnRules(schema, byName, profile, errors);
        }
        return errors.isEmpty()
                ? BaseValidationResultDTO.ok()
                : BaseValidationResultDTO.fail(List.copyOf(errors), meta);
    }

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        return null;
    }

    // ---------------------------
    // Validators
    // ---------------------------

    private void validateBounds(TabularSchemaDTO s, long rows, int cols, List<String> errors) {
        if (s.getMinRows() != null && rows < s.getMinRows()) {
            errors.add("Too few rows: " + rows + " < minRows " + s.getMinRows());
        }
        if (s.getMaxRows() != null && rows > s.getMaxRows()) {
            errors.add("Too many rows: " + rows + " > maxRows " + s.getMaxRows());
        }
        if (s.getMinColumns() != null && cols < s.getMinColumns()) {
            errors.add("Too few columns: " + cols + " < minColumns " + s.getMinColumns());
        }
        if (s.getMaxColumns() != null && cols > s.getMaxColumns()) {
            errors.add("Too many columns: " + cols + " > maxColumns " + s.getMaxColumns());
        }
    }

    private void validateRequiredColumns(TabularSchemaDTO
                                                 s, Map<String, ColumnProfile> byName, List<String> errors) {
        List<String> req = s.getRequiredColumns();
        if (req == null || req.isEmpty()) return;

        for (String col : req) {
            if (!byName.containsKey(col)) {
                errors.add("Missing required column: '" + col + "'");
            }
        }
    }

    private void validateAllowNulls(TabularSchemaDTO s, FileProfile profile, List<String> errors) {
        Boolean allowNulls = s.getProhibitedNulls();
        if (allowNulls == null || allowNulls) return;

        for (ColumnProfile cp : safe(profile.getColumns())) {
            if (cp.missing() > 0) {
                errors.add("allowNulls=false but column '" + cp.name() + "' has missing=" + cp.missing());
                return; // first-failure
            }
        }
    }

    private void validateAllowOnlyNumbers(TabularSchemaDTO s, FileProfile profile, List<String> errors) {
        if (!Boolean.TRUE.equals(s.getAllowOnlyNumbers())) return;

        for (ColumnProfile cp : safe(profile.getColumns())) {
            String t = normalizeType(cp.type());
            if (!isNumericType(t)) {
                errors.add("allowOnlyNumbers=true but column '" + cp.name() + "' is type=" + cp.type());
                return;
            }
        }
    }

    private void validateColumnRules(TabularSchemaDTO s,
                                     Map<String, ColumnProfile> byName,
                                     FileProfile profile,
                                     List<String> errors) {

        Map<String, ColumnRuleDTO> rules = s.getColumns();
        if (rules == null || rules.isEmpty()) return;

        for (Map.Entry<String, ColumnRuleDTO> e : rules.entrySet()) {
            String colName = e.getKey();
            ColumnRuleDTO rule = e.getValue();

            ColumnProfile cp = byName.get(colName);
            if (cp == null) {
                errors.add("Rule provided for unknown column '" + colName + "'");
                continue;
            }

            // nullable check (rule-level)
            boolean nullable = rule.getNullable() == null || Boolean.TRUE.equals(rule.getNullable());
            if (!nullable && cp.missing() > 0) {
                errors.add("Column '" + colName + "' is not nullable but missing=" + cp.missing());
                return;
            }

            // type check (rule.type vs ColumnProfile.type)
            if (rule.getType() != null) {
                if (!matchesRuleType(rule.getType(), cp.type())) {
                    errors.add("Column '" + colName + "' type mismatch: expected " + rule.getType() + " but profile is " + cp.type());
                    return;
                }
            }

            // numeric min/max (rule.min/max)
            if (rule.getMin() != null || rule.getMax() != null) {
                String t = normalizeType(cp.type());
                if (!isNumericType(t)) {
                    errors.add("Column '" + colName + "' has min/max rule but is not numeric (profile type=" + cp.type() + ")");
                    return;
                }
                if (rule.getMin() != null && cp.min() != null && cp.min() < rule.getMin()) {
                    errors.add("Column '" + colName + "' min " + cp.min() + " < rule.min " + rule.getMin());
                    return;
                }
                if (rule.getMax() != null && cp.max() != null && cp.max() > rule.getMax()) {
                    errors.add("Column '" + colName + "' max " + cp.max() + " > rule.max " + rule.getMax());
                    return;
                }
            }

            // enumValues check (categorical)
            if (rule.getEnumValues() != null && !rule.getEnumValues().isEmpty()) {
                // We only have topCategories, not full distinct set.
                // We can still fail if topCategories contains values not in enum, OR warn if coverage unknown.
                Set<String> allowed = new HashSet<>(rule.getEnumValues());
                List<Map.Entry<String, Integer>> tops = cp.topCategories();
                if (tops != null) {
                    for (Map.Entry<String, Integer> ent : tops) {
                        if (ent != null && ent.getKey() != null && !allowed.contains(ent.getKey())) {
                            errors.add("Column '" + colName + "' has category '" + ent.getKey() + "' not in enumValues");
                            return;
                        }
                    }
                }
            }

            // regex check
            if (rule.getRegex() != null && !rule.getRegex().isBlank()) {
                // With only profile stats, regex can't be proven for all values.
                // Best effort: scan sampleRows for this column if sample rows are CSV strings or map-like.
                // Since sampleRows is List<String>, we can only do heuristic:
                // - If sampleRows contain raw row strings with delimiter, we can split and check.
                // If this heuristic is unreliable in your system, remove this block.
                Pattern p;
                try {
                    p = Pattern.compile(rule.getRegex());
                } catch (Exception ex) {
                    errors.add("Invalid regex for column '" + colName + "': " + ex.getMessage());
                    return;
                }

                // try sample validation if possible
                List<String> sample = profile.getSampleRows();
                if (sample != null && !sample.isEmpty()) {
                    // if we can locate index by ordering in profile.columns
                    int colIdx = indexOfColumn(profile.getColumns(), colName);
                    if (colIdx >= 0) {
                        for (String rawRow : sample) {
                            if (rawRow == null) continue;
                            // naive split by comma OR tab depending on typical use
                            String[] parts = rawRow.split("[,\t]", -1);
                            if (colIdx < parts.length) {
                                String v = parts[colIdx].trim();
                                if (!v.isEmpty() && !p.matcher(v).matches()) {
                                    errors.add("Column '" + colName + "' regex mismatch on sample value: '" + v + "'");
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private BaseValidationResultDTO fail(List<String> errors, Map<String, Object> meta, String msg) {
        errors.add(msg);
        return BaseValidationResultDTO.fail(List.copyOf(errors), meta);
    }

    private List<ColumnProfile> safe(List<ColumnProfile> cols) {
        return cols == null ? List.of() : cols;
    }

    private String normalizeType(String t) {
        return t == null ? "" : t.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isNumericType(String t) {
        return "INTEGER".equals(t) || "NUMBER".equals(t);
    }

    private boolean matchesRuleType(ToolConfigHyperParamDataType ruleType, String profileType) {
        String pt = normalizeType(profileType);

        return switch (ruleType) {
            case INTEGER -> "INTEGER".equals(pt);
            case FLOAT -> "NUMBER".equals(pt) || "INTEGER".equals(pt);
            case BOOLEAN -> "BOOLEAN".equals(pt);
            case STRING ->
                    "TEXT".equals(pt) || "DATETIME".equals(pt) || "BOOLEAN".equals(pt) || "INTEGER".equals(pt) || "NUMBER".equals(pt);
            case CATEGORICAL -> "TEXT".equals(pt) || "DATETIME".equals(pt);
            default -> true;
        };
    }

    private int indexOfColumn(List<ColumnProfile> cols, String name) {
        if (cols == null || name == null) return -1;
        for (int i = 0; i < cols.size(); i++) {
            ColumnProfile c = cols.get(i);
            if (c != null && name.equals(c.name())) return i;
        }
        return -1;
    }
}
