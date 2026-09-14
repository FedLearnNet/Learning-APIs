package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConnectorHelperTest {

    @Test
    void includesEveryColumnReadByMapping() {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn("measurement");
        mapping.setVisitIdMapping("visit_id");
        mapping.setVisitTimestampMapping("visit_time");
        ConnectorValueMappingConfigDTO valueMapping = new ConnectorValueMappingConfigDTO();
        valueMapping.setMappingColumn("kind");
        valueMapping.setValueColumn("value");
        mapping.setValueMappingConfig(valueMapping);

        ConnectorEntity connector = connector(mapping);

        assertEquals(
                List.of("measurement", "visit_id", "visit_time", "kind", "value"),
                ConnectorHelper.getUsedColumns(connector));
    }

    @Test
    void resolvesChainedTransformerOutputsBackToRawInputs() {
        ConnectorEntity connector = connector(mapping("final_result"));
        connector.setTransformers(new ArrayList<>(List.of(
                transformer(0, Map.of("left", "raw_a", "right", "raw_b"), Map.of("value", "intermediate")),
                transformer(1, Map.of("value", "intermediate", "weight", "raw_c"), Map.of("value", "final_result"))
        )));

        Set<String> used = Set.copyOf(ConnectorHelper.getUsedColumns(connector));

        assertEquals(Set.of("raw_a", "raw_b", "raw_c"), used);
        assertFalse(used.contains("intermediate"));
        assertFalse(used.contains("final_result"));
    }

    @Test
    void ignoresInputsOfATransformerWhoseOutputIsNotUsed() {
        ConnectorEntity connector = connector(mapping("mapped"));
        connector.setTransformers(new ArrayList<>(List.of(
                transformer(0, Map.of("value", "unrelated_raw"), Map.of("value", "unrelated_output"))
        )));

        assertEquals(List.of("mapped"), ConnectorHelper.getUsedColumns(connector));
    }

    @Test
    void keepsTheSourceOfAnInPlaceCellTransformer() {
        ConnectorEntity connector = connector(mapping("age"));
        ConnectorTransformerEntity transformer = transformer(0, Map.of("mode", "TITLE"), Map.of());
        transformer.setColumn("age");
        connector.setTransformers(new ArrayList<>(List.of(transformer)));

        List<String> filtered = ConnectorHelper.filterOurUnsuedColumns(
                connector, List.of(table("patients", "age", "unused")));

        assertEquals(List.of("patients::age"), filtered);
    }

    @Test
    void honorsTableQualifiersAndNormalizesTableNames() {
        ConnectorEntity connector = connector(mapping("lab results::VALUE"));

        List<String> filtered = ConnectorHelper.filterOurUnsuedColumns(connector, List.of(
                table("Lab_Results", " Value ", "unused"),
                table("vitals", "value", "heart_rate")
        ));

        assertEquals(List.of("labresults::Value"), filtered);
    }

    @Test
    void anUnqualifiedUsedColumnCanMatchItsQualifiedUploadColumn() {
        assertEquals(
                List.of("labs::patient_id", "vitals::patient_id"),
                ConnectorHelper.filterOurUnsuedColumns(
                        List.of("PATIENT_ID"),
                        List.of("labs::patient_id", "vitals::patient_id", "labs::value")));
    }

    @Test
    void mergedFilesKeepUidColumnsAndIgnoreTablesThatDoNotContributeData() {
        ConnectorEntity connector = connector(mapping("labs::value"));
        connector.setMergeConfig(new SheetMergeResultDTO(
                "patient_id",
                Map.of(
                        "labs", "patient_id",
                        "identifiers", "patient_id"
                ),
                "patient_id"
        ));

        List<String> filtered = ConnectorHelper.filterOurUnsuedColumns(connector, List.of(
                table("labs", "patient_id", "value", "unused"),
                table("identifiers", "patient_id"),
                table("not_merged", "patient_id", "ignored")
        ));

        assertEquals(List.of("labs::patient_id", "labs::value"), filtered);
    }

    @Test
    void nonMergedSingleColumnFilesAreStillValidated() {
        ConnectorEntity connector = connector(mapping("value"));

        assertEquals(
                List.of("onlytable::value"),
                ConnectorHelper.filterOurUnsuedColumns(
                        connector, List.of(table("only_table", "value"))));
    }

    private static ConnectorEntity connector(ConnectorMappingDTO... mappings) {
        ConnectorEntity connector = new ConnectorEntity();
        connector.setSchemaMapping(new ArrayList<>(List.of(mappings)));
        connector.setTransformers(new ArrayList<>());
        return connector;
    }

    private static ConnectorMappingDTO mapping(String column) {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn(column);
        return mapping;
    }

    private static ConnectorTransformerEntity transformer(
            int position,
            Map<String, Object> inputs,
            Map<String, Object> outputs
    ) {
        ConnectorTransformerEntity transformer = new ConnectorTransformerEntity();
        transformer.setPosition(position);
        transformer.setInputMapping(inputs);
        transformer.setReturnMapping(outputs);
        return transformer;
    }

    private static ConnectorFileUploadInfoDTO table(String sheet, String... columns) {
        ConnectorFileUploadInfoDTO info = new ConnectorFileUploadInfoDTO();
        info.setSheet(sheet);
        info.setColumns(new ArrayList<>(List.of(columns)));
        return info;
    }
}
