package unit;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.api.statistics.DataStatisticsBO;
import bio.cosy.feddb.local.api.statistics.LocalDataStatisticsDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataStatisticsBOTest {

    @Test
    void getDataStatisticsForPatientAggregatesProfilesAndIds() {
        CohortEntity cohortOne = new CohortEntity();
        cohortOne.setId(10L);
        CohortEntity cohortTwo = new CohortEntity();
        cohortTwo.setId(20L);

        SchemaNodeEntity ageNode = createSchemaNode(100L, "Age", DataTypes.INT);
        SchemaNodeEntity statusNode = createSchemaNode(101L, "Status", DataTypes.STRING);

        PatientEntity patientOne = new PatientEntity();
        patientOne.setId(1L);
        patientOne.setCohort(cohortOne);
        patientOne.setDataEntries(new HashSet<>(Set.of(
                createIntEntry(patientOne, ageNode, 20L),
                createIntEntry(patientOne, ageNode, 30L),
                createStringEntry(patientOne, statusNode, "A")
        )));

        PatientEntity patientTwo = new PatientEntity();
        patientTwo.setId(2L);
        patientTwo.setCohort(cohortTwo);
        patientTwo.setDataEntries(new HashSet<>(Set.of(
                createIntEntry(patientTwo, ageNode, 40L),
                createStringEntry(patientTwo, statusNode, "B")
        )));

        LocalDataStatisticsDTO result = new DataStatisticsBO().getDataStatisticsForPatient(Set.of(patientTwo, patientOne));

        Assertions.assertEquals(List.of(10L, 20L), result.getCohortIds());
        Assertions.assertEquals(List.of(1L, 2L), result.getPatientIds());
        Assertions.assertEquals(2, result.getProperties().size());

        ColumnProfile ageProfile = result.getProperties().get(0);
        Assertions.assertEquals("Age", ageProfile.name());
        Assertions.assertEquals("INTEGER", ageProfile.type());
        Assertions.assertEquals(3L, ageProfile.count());
        Assertions.assertEquals(0L, ageProfile.missing());
        Assertions.assertEquals(3, ageProfile.uniqueValues());
        Assertions.assertEquals(30.0, ageProfile.mean());
        Assertions.assertEquals(20.0, ageProfile.min());
        Assertions.assertEquals(25.0, ageProfile.p25());
        Assertions.assertEquals(30.0, ageProfile.median());
        Assertions.assertEquals(35.0, ageProfile.p75());
        Assertions.assertEquals(40.0, ageProfile.max());
        Assertions.assertNull(ageProfile.topCategories());

        ColumnProfile statusProfile = result.getProperties().get(1);
        Assertions.assertEquals("Status", statusProfile.name());
        Assertions.assertEquals("TEXT", statusProfile.type());
        Assertions.assertEquals(2L, statusProfile.count());
        Assertions.assertEquals(0L, statusProfile.missing());
        Assertions.assertEquals(2, statusProfile.uniqueValues());
        Assertions.assertNotNull(statusProfile.topCategories());
        Assertions.assertEquals("A", statusProfile.topCategories().get(0).getKey());
        Assertions.assertEquals(1, statusProfile.topCategories().get(0).getValue());
        Assertions.assertEquals("B", statusProfile.topCategories().get(1).getKey());
        Assertions.assertEquals(1, statusProfile.topCategories().get(1).getValue());
    }

    @Test
    void getStatisticsForAllAggregatesPatientsFromAllCohorts() throws Exception {
        CohortEntity cohortOne = new CohortEntity();
        cohortOne.setId(10L);
        CohortEntity cohortTwo = new CohortEntity();
        cohortTwo.setId(20L);

        SchemaNodeEntity ageNode = createSchemaNode(100L, "Age", DataTypes.INT);

        PatientEntity patientOne = new PatientEntity();
        patientOne.setId(1L);
        patientOne.setCohort(cohortOne);
        patientOne.setDataEntries(new HashSet<>(Set.of(createIntEntry(patientOne, ageNode, 20L))));

        PatientEntity patientTwo = new PatientEntity();
        patientTwo.setId(2L);
        patientTwo.setCohort(cohortTwo);
        patientTwo.setDataEntries(new HashSet<>(Set.of(createIntEntry(patientTwo, ageNode, 40L))));

        cohortOne.setPatients(Set.of(patientOne));
        cohortTwo.setPatients(Set.of(patientTwo));

        CohortAO cohortAO = new CohortAO() {
            @Override
            public List<CohortEntity> listAll() {
                return List.of(cohortOne, cohortTwo);
            }
        };

        DataStatisticsBO bo = new DataStatisticsBO();
        injectField(bo, "cohortAO", cohortAO);

        LocalDataStatisticsDTO result = bo.getStatisticsForAll();

        Assertions.assertEquals(List.of(10L, 20L), result.getCohortIds());
        Assertions.assertEquals(List.of(1L, 2L), result.getPatientIds());
        Assertions.assertEquals(1, result.getProperties().size());
        Assertions.assertEquals("INTEGER", result.getProperties().getFirst().type());
        Assertions.assertEquals(2L, result.getProperties().getFirst().count());
    }

    private static SchemaNodeEntity createSchemaNode(Long id, String name, DataTypes type) {
        DataTypeEntity dataType = new DataTypeEntity();
        dataType.setType(type);

        SchemaNodeEntity node = new SchemaNodeEntity();
        node.setId(id);
        node.setName(name);
        node.setDataType(dataType);
        return node;
    }

    private static PatientDataEntryEntity createIntEntry(PatientEntity patient, SchemaNodeEntity node, Long value) {
        PatientDataEntryEntity entry = new PatientDataEntryEntity();
        entry.setPatient(patient);
        entry.setSchemaNode(node);
        entry.setValueInt(value);
        return entry;
    }

    private static PatientDataEntryEntity createStringEntry(PatientEntity patient, SchemaNodeEntity node, String value) {
        PatientDataEntryEntity entry = new PatientDataEntryEntity();
        entry.setPatient(patient);
        entry.setSchemaNode(node);
        entry.setValueString(value);
        return entry;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
