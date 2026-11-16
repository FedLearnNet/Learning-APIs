package bio.cosy.feddb.local.api.importer.files.table;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableSampleFillerTest {

    private static final List<String> COLUMNS = List.of("id", "weight", "payer_code", "never");

    @Test
    void keepsTheFirstRowsAndAddsTheFirstLaterRowForEachEmptyColumn() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            rows.add(row(i, i == 23 || i == 40 ? "[75-100)" : "?", i == 188 ? "MC" : "?"));
        }

        TableSample sample = TableSample.withFillers(table(rows), 5, 50);

        // Rows 1-5, then row 23 for "weight" and row 188 for "payer_code"; "never" adds nothing.
        assertEquals(List.of(1, 2, 3, 4, 5, 23, 188), ids(sample));
        assertEquals(COLUMNS, sample.getColumns());
    }

    @Test
    void oneRowCanFillSeveralColumns() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            rows.add(row(i, i >= 30 ? "[0-25)" : "?", i >= 30 ? "SP" : "?"));
        }

        assertEquals(List.of(1, 2, 30), ids(TableSample.withFillers(table(rows), 2, 50)));
    }

    @Test
    void columnsAlreadyFilledInTheFirstRowsAddNoFiller() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            rows.add(row(i, "[0-25)", "SP"));
        }

        assertEquals(List.of(1, 2, 3), ids(TableSample.withFillers(table(rows), 3, 50)));
    }

    @Test
    void stopsAtTheFillerLimit() {
        List<String> columns = List.of("id", "a", "b", "c");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", 1, "a", "?", "b", "?", "c", "?"));
        rows.add(Map.of("id", 2, "a", "x", "b", "?", "c", "?"));
        rows.add(Map.of("id", 3, "a", "?", "b", "y", "c", "?"));
        rows.add(Map.of("id", 4, "a", "?", "b", "?", "c", "z"));

        TableSample sample = TableSample.withFillers(
                new TableData(columns, rows, new ArrayList<>()), 1, 2);

        assertEquals(List.of(1, 2, 3), ids(sample));
    }

    @Test
    void aTableShorterThanTheHeadIsKeptWhole() {
        List<Map<String, Object>> rows = List.of(row(1, "?", "?"), row(2, "?", "?"));

        assertEquals(List.of(1, 2), ids(TableSample.withFillers(table(rows), 100, 50)));
    }

    private static TableData table(List<Map<String, Object>> rows) {
        return new TableData(COLUMNS, new ArrayList<>(rows), new ArrayList<>());
    }

    private static Map<String, Object> row(int id, String weight, String payerCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("weight", weight);
        row.put("payer_code", payerCode);
        row.put("never", "?");
        return row;
    }

    private static List<Integer> ids(TableSample sample) {
        return sample.getRows().stream().map(row -> (Integer) row.get("id")).toList();
    }
}
