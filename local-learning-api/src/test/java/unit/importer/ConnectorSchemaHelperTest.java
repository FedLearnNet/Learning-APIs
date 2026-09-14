package unit.importer;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.api.cohort.CohortDetailDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorSchemaHelper;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingMode;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueTargetDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ConnectorSchemaHelperTest {

    @Inject
    ConnectorSchemaHelper schemaHelper;

    @Test
    void validateAndRematchMappingSchemaIdResolvesNestedMappings() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);

        ConnectorMappingDTO ageMapping = new ConnectorMappingDTO();
        ageMapping.setColumn("age");
        ageMapping.setMapping("Demographics.Age");
        ageMapping.setSchemaId(999L);

        ConnectorMappingDTO patientMapping = new ConnectorMappingDTO();
        patientMapping.setColumn("patient_id");
        patientMapping.setMapping("Unique Patient ID");

        connector.setSchemaMapping(List.of(ageMapping, patientMapping));

        ConnectorDTO result = schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode());

        assertEquals(101L, result.getSchemaMapping().get(0).getSchemaId());
        assertNull(result.getSchemaMapping().get(1).getSchemaId());
    }

    @Test
    void validateAndRematchMappingSchemaIdRejectsUnknownMapping() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn("weight");
        mapping.setMapping("Demographics.Weight");

        connector.setSchemaMapping(List.of(mapping));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode())
        );

        assertEquals(
                "Schema mapping fields not found in cohort schema: Demographics.Weight",
                error.getMessage()
        );
    }

    @Test
    void validateAndRematchMappingSchemaIdResolvesAdvancedTargets() {
        ConnectorValueTargetDTO target = new ConnectorValueTargetDTO();
        target.setSourceValue("age");
        target.setValue("Demographics.Age");
        target.setSchemaId(999L);

        ConnectorValueMappingConfigDTO valueConfig = new ConnectorValueMappingConfigDTO();
        valueConfig.setMode(ConnectorMappingMode.VALUE_COLUMN);
        valueConfig.setMappingColumn("metric");
        valueConfig.setValueColumn("value");
        valueConfig.setValueMappings(List.of(target));

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn("metric");
        mapping.setValueMappingConfig(valueConfig);

        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);
        connector.setSchemaMapping(List.of(mapping));

        ConnectorDTO result = schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode());

        assertEquals(101L, result.getSchemaMapping().get(0)
                .getValueMappingConfig().getValueMappings().get(0).getSchemaId());
    }

    @Test
    void validateAndRematchMappingSchemaIdRejectsMissingColumn() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setMapping("Demographics.Age");

        connector.setSchemaMapping(List.of(mapping));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode())
        );

        assertEquals(
                "Schema mapping is missing file columns for: Demographics.Age",
                error.getMessage()
        );
    }

    @Test
    void validateAndRematchMappingSchemaIdRejectsInvalidMergeConfig() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);
        connector.setMergeConfig(new SheetMergeResultDTO(
                "patient_uid",
                Map.of("Demographics", ""),
                "patient_uid"
        ));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode())
        );

        assertEquals(
                "Invalid mergeConfig sheet UID mappings: Demographics (missing UID column)",
                error.getMessage()
        );
    }

    @Test
    void validateAndRematchMappingSchemaIdSkipsWhenNoSchemaConfiguration() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setCohortId(1L);

        ConnectorDTO result = schemaHelper.validateAndRematchMappingSchemaId(connector, cohortWithAgeNode());

        assertEquals(connector, result);
    }

    private CohortDetailDTO cohortWithAgeNode() {
        LocalSchemaNodeNestedDTO ageNode = new LocalSchemaNodeNestedDTO();
        ageNode.setId(101L);
        ageNode.setName("Age");
        ageNode.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);

        LocalSchemaNodeNestedDTO demographicsGroup = new LocalSchemaNodeNestedDTO();
        demographicsGroup.setId(100L);
        demographicsGroup.setName("Demographics");
        demographicsGroup.setType(SchemaNodeType.GROUP);
        demographicsGroup.setChildNodes(new LinkedHashSet<>(Set.of(ageNode)));

        LocalSchemaRootNodeDTO schemaRoot = new LocalSchemaRootNodeDTO();
        schemaRoot.setChildNodes(new LinkedHashSet<>(Set.of(demographicsGroup)));

        CohortDetailDTO cohort = new CohortDetailDTO();
        cohort.setId(1L);
        cohort.setSchemaRoot(schemaRoot);
        return cohort;
    }
}
