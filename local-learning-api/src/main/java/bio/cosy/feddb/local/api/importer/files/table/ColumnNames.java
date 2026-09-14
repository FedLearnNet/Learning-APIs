package bio.cosy.feddb.local.api.importer.files.table;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.*;

public final class ColumnNames {

    //Separates a table name from a column name where a merged table has to disambiguate one.
    public static final String QUALIFIER = "::";

    // Byte-order mark and the zero-width characters exports leave in header cells.
    private static final String INVISIBLE = "\uFEFF\u200B\u200C\u200D";

    // A column name as it is compared: invisible characters and surrounding space removed.
    public static String column(String name) {
        return strip(name).trim();
    }

    // A table name as it is compared: case, whitespace and underscores are not part of its identity.
    public static String table(String name) {
        return StringUtils.deleteWhitespace(strip(name).toLowerCase(Locale.ROOT).replace("_", ""));
    }

    public static String normalize(String column) {
        return ColumnNames.unqualify(ColumnNames.column(column)).toLowerCase(Locale.ROOT);
    }

    // The table a file or archive entry stands for: its file name, without directory or extension.
    public static String tableOfFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        String name = Path.of(fileName).getFileName().toString();
        return name.lastIndexOf('.') > 0 ? StringUtils.substringBeforeLast(name, ".") : name;
    }

    /**
     * {@code table::column}, the form a merged table gives a column shared by several tables.
     */
    public static String qualify(String table, String column) {
        return column == null ? null : table + QUALIFIER + column;
    }

    /**
     * The column name without its table qualifier, if it has one.
     */
    public static String unqualify(String column) {
        int separator = column == null ? -1 : column.lastIndexOf(QUALIFIER);
        return separator < 0 ? column : column.substring(separator + QUALIFIER.length());
    }

    /**
     * The table part of {@code table::column}, or {@code null} for an unqualified column.
     */
    public static String qualifier(String column) {
        int separator = column == null ? -1 : column.lastIndexOf(QUALIFIER);
        return separator < 0 ? null : column.substring(0, separator);
    }

    public static boolean matches(String configured, String available) {
        String configuredColumn = normalize(configured);
        if (configuredColumn.isEmpty() || !configuredColumn.equals(normalize(available))) {
            return false;
        }
        String configuredTable = qualifier(column(configured));
        String availableTable = qualifier(column(available));
        return configuredTable == null
                || availableTable == null
                || table(configuredTable).equals(table(availableTable));
    }

    public static List<String> unqualify(List<String> columns) {
        return columns.stream()
                .map(ColumnNames::unqualify)
                .distinct()
                .toList();
    }

    public static String find(List<String> columns, String expected) {
        if (columns == null || expected == null) {
            return null;
        }
        String wanted = column(expected);
        return columns.stream()
                .filter(candidate -> Objects.equals(column(candidate), wanted))
                .findFirst()
                .orElse(null);
    }


    public static <T> T configuredFor(Map<String, T> byTable, String table) {
        if (byTable == null || byTable.isEmpty()) {
            return null;
        }
        if (byTable.containsKey(table)) {
            return byTable.get(table);
        }
        String wanted = table(table);
        return byTable.entrySet().stream()
                .filter(entry -> table(entry.getKey()).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }


    public static Set<String> duplicatesAcross(Map<String, List<String>> columnsByTable) {
        Map<String, Set<String>> tablesByColumn = new LinkedHashMap<>();
        columnsByTable.forEach((table, columns) -> {
            if (columns == null) {
                return;
            }
            columns.stream()
                    .filter(Objects::nonNull)
                    .forEach(column -> tablesByColumn
                            .computeIfAbsent(column, ignored -> new LinkedHashSet<>())
                            .add(table));
        });
        tablesByColumn.values().removeIf(tables -> tables.size() < 2);
        return new LinkedHashSet<>(tablesByColumn.keySet());
    }

    public static String preview(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return "[]";
        }
        int limit = Math.min(columns.size(), 12);
        List<String> head = columns.subList(0, limit);
        return columns.size() > limit ? head + " ... (" + columns.size() + " total)" : head.toString();
    }

    private static String strip(String value) {
        return value == null ? "" : StringUtils.replaceChars(value, INVISIBLE, "");
    }
}
