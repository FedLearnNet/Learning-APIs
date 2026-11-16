package bio.cosy.feddb.local.api.importer.run.preview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewRowSelectorTest {

    private static final List<String> COLUMNS = List.of("id", "weight", "payer_code");

    @Test
    void aPoolNoLargerThanTheRequestIsReturnedAsItIs() {
        List<Map<String, Object>> pool = List.of(row(1, "?", "?"), row(2, "?", "?"));

        assertSame(pool, PreviewRowSelector.select(pool, COLUMNS, 2));
    }

    @Test
    void rowsThatFillSparseColumnsArePickedOverEarlierEmptyOnes() {
        List<Map<String, Object>> pool = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            pool.add(row(i, "?", "?"));
        }
        pool.add(row(21, "[75-100)", "?"));
        pool.add(row(22, "?", "MC"));

        List<Map<String, Object>> selected = PreviewRowSelector.select(pool, COLUMNS, 5);

        assertEquals(5, selected.size());
        assertTrue(selected.stream().anyMatch(row -> "[75-100)".equals(row.get("weight"))));
        assertTrue(selected.stream().anyMatch(row -> "MC".equals(row.get("payer_code"))));
    }

    @Test
    void oneRowFillingBothColumnsIsPreferredOverTwoRowsFillingOneEach() {
        List<Map<String, Object>> pool = List.of(
                row(1, "?", "?"),
                row(2, "[50-75)", "?"),
                row(3, "?", "SP"),
                row(4, "[75-100)", "MC"));

        List<Map<String, Object>> selected = PreviewRowSelector.select(pool, COLUMNS, 1);

        assertEquals(List.of(4), ids(selected));
    }

    @Test
    void theSelectedRowsKeepTheirOrderInTheFile() {
        List<Map<String, Object>> pool = List.of(
                row(1, "?", "?"),
                row(2, "?", "?"),
                row(3, "?", "MC"),
                row(4, "?", "?"),
                row(5, "[0-25)", "?"));

        assertEquals(List.of(3, 5), ids(PreviewRowSelector.select(pool, COLUMNS, 2)));
    }

    @Test
    void whenNothingIsLeftToFillTheFullestEarliestRowsTakeTheRemainingSlots() {
        List<Map<String, Object>> pool = List.of(
                row(1, "?", "?"),
                row(2, "?", "?"),
                row(3, "?", "?"),
                row(4, "?", "?"));

        // Every row only has its id, so the first rows in the file are kept.
        assertEquals(List.of(1, 2), ids(PreviewRowSelector.select(pool, COLUMNS, 2)));
    }

    @Test
    void questionMarksAndMissingTokensCountAsEmpty() {
        assertTrue(PreviewCells.isEmpty("?"));
        assertTrue(PreviewCells.isEmpty(" ? "));
        assertTrue(PreviewCells.isEmpty(""));
        assertTrue(PreviewCells.isEmpty("NA"));
        assertTrue(PreviewCells.isEmpty("null"));
        assertTrue(PreviewCells.isEmpty(null));
        assertTrue(PreviewCells.isEmpty(List.of()));
        assertTrue(!PreviewCells.isEmpty("0"));
        assertTrue(!PreviewCells.isEmpty("Caucasian"));
    }

    private static Map<String, Object> row(int id, String weight, String payerCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("weight", weight);
        row.put("payer_code", payerCode);
        return row;
    }

    private static List<Integer> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (Integer) row.get("id")).toList();
    }
}
