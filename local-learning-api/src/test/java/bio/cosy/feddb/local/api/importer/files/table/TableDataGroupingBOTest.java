package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableDataGroupingBOTest {

    @Test
    void indexesAndGroupsRowsByColumnAcrossMultipleBuckets() throws IOException {
        TableData data = new TableData(List.of("pid", "age", "gender"), new ArrayList<>(), new ArrayList<>());
        data.appendRow(row("P2", 50, "F"));
        data.appendRow(row("P1", 40, "M"));
        data.appendRow(row("P3", 33, "M"));
        data.appendRow(row("P1", 41, "M"));
        data.appendRow(row("P2", 51, "F"));

        try (TableDataGroups groups = createBo().groupByColumn(data, "pid", 2)) {
            assertEquals(3L, groups.groupCount());

            Map<String, List<Map<String, Object>>> groupedRows = new LinkedHashMap<>();
            try (TableDataGroupCursor cursor = groups.cursor()) {
                TableDataGroup group;
                while ((group = cursor.next()) != null) {
                    assertTrue(!groupedRows.containsKey(group.key()), "duplicate group for key " + group.key());
                    groupedRows.put(group.key(), group.rows());
                }
            }

            assertEquals(List.of("P1", "P2", "P3"), groupedRows.keySet().stream().sorted().toList());
            assertEquals(2, groupedRows.get("P1").size());
            assertEquals(2, groupedRows.get("P2").size());
            assertEquals(1, groupedRows.get("P3").size());
            assertEquals(40, groupedRows.get("P1").getFirst().get("age"));
        }
    }

    @Test
    void groupsAllRowsUnderNullWhenColumnIsAbsent() throws IOException {
        TableData data = new TableData(List.of("pid", "age"), new ArrayList<>(), new ArrayList<>());
        data.appendRow(row("P1", 40));
        data.appendRow(row("P2", 41));

        try (TableDataGroups groups = createBo().groupByColumn(data, "missing", 50)) {
            assertEquals(1L, groups.groupCount());

            try (TableDataGroupCursor cursor = groups.cursor()) {
                TableDataGroup group = cursor.next();
                assertEquals("null", group.key());
                assertEquals(2, group.rows().size());
                assertNull(cursor.next());
            }
        }
    }

    @Test
    void groupsDiskBackedRowsWithoutCopyingSourceRows() throws IOException {
        TableData data = TableData.diskBacked(List.of("pid", "age", "gender"), new ArrayList<>(), null);
        data.appendRow(row("P1", 40, "M"));
        data.appendRow(row("P2", 50, "F"));
        data.appendRow(row("P1", 41, "M"));

        try (TableDataGroups groups = createBo().groupByColumn(data, "pid", 2)) {
            assertEquals(2L, groups.groupCount());
            assertTrue(!data.isDiskBacked());

            try (TableDataGroupCursor cursor = groups.cursor()) {
                TableDataGroup first = cursor.next();
                TableDataGroup second = cursor.next();
                assertTrue(List.of(first.key(), second.key()).contains("P1"));
                assertTrue(List.of(first.key(), second.key()).contains("P2"));
                assertNull(cursor.next());
            }
        }
    }

    private static TableDataGroupingBO createBo() {
        FLNetClientConfig config = mock(FLNetClientConfig.class);
        FLNetClientConfig.ConnectorConfig connectorConfig = mock(FLNetClientConfig.ConnectorConfig.class);
        when(config.connector()).thenReturn(connectorConfig);
        when(connectorConfig.etlWorkDirectory()).thenReturn(Optional.empty());

        TableDataGroupingBO bo = new TableDataGroupingBO();
        bo.config = config;
        return bo;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        List<String> columns = Arrays.asList("pid", "age", "gender");
        for (int i = 0; i < values.length; i++) {
            row.put(columns.get(i), values[i]);
        }
        return row;
    }
}
