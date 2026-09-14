package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotMode;
import bio.cosy.feddb.local.api.importer.extract.PivotValueFormat;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDataPivotBOTest {

    @Test
    void pivotsDiskBackedTableWithoutMaterializingTheOutput() {
        TableData source = TableData.diskBacked(
                List.of("field", "patient-1", "patient-2"),
                new ArrayList<>(),
                null
        );
        source.appendRow(row("field", "patient_id", "patient-1", "p1", "patient-2", "p2"));
        source.appendRow(row("field", "age", "patient-1", "31", "patient-2", "42"));

        TableDataPivotBO pivotBO = new TableDataPivotBO();
        TableData pivoted = pivotBO.pivot(0, source);
        try {
            assertTrue(pivoted.isDiskBacked());
            assertEquals(List.of("field", "patient_id", "age"), pivoted.getColumns());
            assertEquals(List.of(
                    row("field", "patient-1", "patient_id", "p1", "age", "31"),
                    row("field", "patient-2", "patient_id", "p2", "age", "42")
            ), pivoted.getRows());
        } finally {
            pivoted.close();
            source.close();
        }
    }

    @Test
    void singleTableUsesConfiguredIndexRegardlessOfMapKey() {
        TableData source = new TableData(
                List.of("patient-1", "field"),
                List.of(
                        row("patient-1", "p1", "field", "patient_id"),
                        row("patient-1", "31", "field", "age")
                ),
                new ArrayList<>()
        );

        TableData pivoted = new TableDataPivotBO().pivot(
                new PivotConfigDTO(Map.of("ignored-for-single-table", 1)),
                source
        );
        try {
            assertEquals(List.of("field", "patient_id", "age"), pivoted.getColumns());
            assertEquals(
                    List.of(row("field", "patient-1", "patient_id", "p1", "age", "31")),
                    pivoted.getRows()
            );
        } finally {
            pivoted.close();
        }
    }

    @Test
    void singleTableMapUsesConfiguredIndexRegardlessOfMapKey() {
        TableData source = new TableData(
                List.of("patient-1", "field"),
                List.of(
                        row("patient-1", "p1", "field", "patient_id"),
                        row("patient-1", "31", "field", "age")
                ),
                new ArrayList<>()
        );

        ParsedTables result = new TableDataPivotBO().pivotTables(
                new PivotConfigDTO(Map.of("different-name", 1)),
                ParsedTables.of(Map.of("actual-name", source))
        );
        TableData pivoted = result.tables().get("actual-name");
        try {
            assertEquals(List.of("field", "patient_id", "age"), pivoted.getColumns());
            assertEquals("p1", pivoted.getRows().getFirst().get("patient_id"));
        } finally {
            pivoted.close();
        }
    }

    @Test
    void rejectsDuplicateGeneratedColumns() {
        TableData source = new TableData(
                List.of("field", "patient-1"),
                List.of(
                        row("field", "age", "patient-1", "31"),
                        row("field", "age", "patient-1", "32")
                ),
                new ArrayList<>()
        );

        assertThrows(BadRequestException.class, () -> new TableDataPivotBO().pivot(0, source));
    }

    @Test
    void oneHotPivotKeepsDuplicateValuesOnTheirSourceRows() {
        TableData source = new TableData(
                List.of("subject_id", "hadm_id", "seq_num", "icd_code", "icd_version"),
                List.of(
                        row("subject_id", "10035185", "hadm_id", "22580999", "seq_num", 3,
                                "icd_code", "4139", "icd_version", 9),
                        row("subject_id", "10035185", "hadm_id", "22580999", "seq_num", 10,
                                "icd_code", "V707", "icd_version", 9),
                        row("subject_id", "10035185", "hadm_id", "22580999", "seq_num", 11,
                                "icd_code", "4139", "icd_version", 9),
                        row("subject_id", "10009049", "hadm_id", "22995465", "seq_num", 2,
                                "icd_code", "4829", "icd_version", 9)
                ),
                new ArrayList<>()
        );
        PivotConfigDTO config = new PivotConfigDTO(Map.of("diagnoses.csv", 3));
        config.setMode(Map.of("diagnoses.csv", PivotMode.ONE_HOT));

        TableData pivoted = new TableDataPivotBO().pivot(config, source);
        try {
            assertEquals(
                    List.of("subject_id", "hadm_id", "seq_num", "icd_version", "4139", "V707", "4829"),
                    pivoted.getColumns()
            );
            assertEquals(List.of(
                    row("subject_id", "10035185", "hadm_id", "22580999",
                            "seq_num", 3, "icd_version", 9,
                            "4139", true, "V707", false, "4829", false),
                    row("subject_id", "10035185", "hadm_id", "22580999",
                            "seq_num", 10, "icd_version", 9,
                            "4139", false, "V707", true, "4829", false),
                    row("subject_id", "10035185", "hadm_id", "22580999",
                            "seq_num", 11, "icd_version", 9,
                            "4139", true, "V707", false, "4829", false),
                    row("subject_id", "10009049", "hadm_id", "22995465",
                            "seq_num", 2, "icd_version", 9,
                            "4139", false, "V707", false, "4829", true)
            ), pivoted.getRows());
        } finally {
            pivoted.close();
        }
    }

    @Test
    void oneHotPivotAppliesPrefixAndConfiguredValueFormat() {
        TableData source = new TableData(
                List.of("patient_id", "icd_code"),
                List.of(
                        row("patient_id", "p1", "icd_code", "A"),
                        row("patient_id", "p1", "icd_code", "B"),
                        row("patient_id", "p2", "icd_code", "A")
                ),
                new ArrayList<>()
        );
        PivotConfigDTO config = new PivotConfigDTO(Map.of("diagnoses.csv", 1));
        config.setMode(Map.of("diagnoses.csv", PivotMode.ONE_HOT));
        config.setPrefix(Map.of("diagnoses.csv", "icd_"));
        config.setValueFormat(Map.of("diagnoses.csv", PivotValueFormat.YES_NO));

        TableData yesNo = new TableDataPivotBO().pivot(config, source);
        try {
            assertEquals(List.of("patient_id", "icd_A", "icd_B"), yesNo.getColumns());
            assertEquals(List.of(
                    row("patient_id", "p1", "icd_A", "yes", "icd_B", "no"),
                    row("patient_id", "p1", "icd_A", "no", "icd_B", "yes"),
                    row("patient_id", "p2", "icd_A", "yes", "icd_B", "no")
            ), yesNo.getRows());
        } finally {
            yesNo.close();
        }

        config.setValueFormat(Map.of("diagnoses.csv", PivotValueFormat.ONE_ZERO));
        TableData oneZero = new TableDataPivotBO().pivot(config, source);
        try {
            assertEquals(List.of(
                    row("patient_id", "p1", "icd_A", 1, "icd_B", 0),
                    row("patient_id", "p1", "icd_A", 0, "icd_B", 1),
                    row("patient_id", "p2", "icd_A", 1, "icd_B", 0)
            ), oneZero.getRows());
        } finally {
            oneZero.close();
        }
    }

    @Test
    void oneHotPivotUsesSourceRowPositionWithoutGroupColumns() {
        TableData source = new TableData(
                List.of("patient_id", "icd_code"),
                List.of(
                        row("patient_id", "p1", "icd_code", "A"),
                        row("patient_id", "p1", "icd_code", "B")
                ),
                new ArrayList<>()
        );
        PivotConfigDTO config = new PivotConfigDTO(Map.of("diagnoses.csv", 1));
        config.setMode(Map.of("diagnoses.csv", PivotMode.ONE_HOT));

        TableData pivoted = new TableDataPivotBO().pivot(config, source);
        try {
            assertEquals(List.of("patient_id", "A", "B"), pivoted.getColumns());
            assertEquals(List.of(
                    row("patient_id", "p1", "A", true, "B", false),
                    row("patient_id", "p1", "A", false, "B", true)
            ), pivoted.getRows());
        } finally {
            pivoted.close();
        }
    }

    @Test
    void pivotsAcrossMultipleDiskBatches() {
        List<String> columns = new ArrayList<>();
        columns.add("field");
        Map<String, Object> idRow = new LinkedHashMap<>();
        Map<String, Object> ageRow = new LinkedHashMap<>();
        idRow.put("field", "patient_id");
        ageRow.put("field", "age");
        for (int i = 0; i < 1_030; i++) {
            String column = "patient-" + i;
            columns.add(column);
            idRow.put(column, "p" + i);
            ageRow.put(column, String.valueOf(20 + i));
        }

        TableData source = TableData.diskBacked(columns, new ArrayList<>(), null);
        source.appendRow(idRow);
        source.appendRow(ageRow);
        TableData pivoted = new TableDataPivotBO().pivot(0, source);
        try {
            assertEquals(1_030, pivoted.longSize());
            List<Map<String, Object>> rows = pivoted.getRows();
            assertEquals("p0", rows.getFirst().get("patient_id"));
            assertEquals("p1029", rows.getLast().get("patient_id"));
            assertEquals("1049", rows.getLast().get("age"));
        } finally {
            pivoted.close();
            source.close();
        }
    }

    private Map<String, Object> row(Object... cells) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i += 2) {
            row.put((String) cells[i], cells[i + 1]);
        }
        return row;
    }
}
