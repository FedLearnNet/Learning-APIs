package rest.resource;

import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ApplicationScoped
public class PatientDataTestResource {
    // Test constants of the comprehensive patient (is added in the tests)
    public static final Long TEST_COHORT_ID = 5L; // COHORT_TEST with comprehensive schema
    public static final String COMPREHENSIVE_PATIENT_EXTERNAL_ID = "TEST_PATIENT_COMPREHENSIVE";

    // Schema node IDs for test cohort (comprehensive data types)
    public static final Long COMPREHENSIVE_TEST_STRING_NODE_ID = 18L;
    public static final Long COMPREHENSIVE_TEST_INT_NODE_ID = 13L;
    public static final Long COMPREHENSIVE_TEST_FLOAT_NODE_ID = 14L;
    public static final Long COMPREHENSIVE_TEST_BOOLEAN_NODE_ID = 19L;
    public static final Long COMPREHENSIVE_TEST_DATE_NODE_ID = 16L;
    public static final Long COMPREHENSIVE_TEST_DATETIME_NODE_ID = 17L;
    public static final Long COMPREHENSIVE_TEST_CATEGORICAL_NODE_ID = 21L;

    public static void verifyPatient1DataReduced(PatientDTO patientData) {
        // Patient 1 from SQL data:
        // - Age (schemaNodeId=2): values 25, 26, 27 -> average = 26
        // - Diagnosis (schemaNodeId=3): "Diabetes Type 2", "Hypertension" -> latest = "Hypertension"

        boolean foundAge = false;
        boolean foundDiagnosis = false;

        for (var entry : patientData.getDataEntries()) {
            if (entry.getSchemaNodeId() == 2) { // Age
                foundAge = true;
                assertEquals(26.0, entry.getValue(), "Patient 1 age should be averaged to 26");
            } else if (entry.getSchemaNodeId() == 3) { // Diagnosis
                foundDiagnosis = true;
                assertEquals("Hypertension", entry.getValue(), "Patient 1 diagnosis should be latest: Hypertension");
            }
        }

        assertTrue(foundAge, "Patient 1 should have age data entry");
        assertTrue(foundDiagnosis, "Patient 1 should have diagnosis data entry");
    }

    public static void verifyPatient2DataReduced(PatientDTO patientData) {
        // Patient 2 from SQL data:
        // - Age (schemaNodeId=2): values 30, 31 -> average = 30.5
        // - Diagnosis (schemaNodeId=3): "Asthma respiratory disease" -> latest = "Asthma respiratory disease"

        boolean foundAge = false;
        boolean foundDiagnosis = false;

        for (var entry : patientData.getDataEntries()) {
            if (entry.getSchemaNodeId() == 2) { // Age
                foundAge = true;
                assertEquals(30.5, entry.getValue(), "Patient 2 age should be averaged to 30.5");
            } else if (entry.getSchemaNodeId() == 3) { // Diagnosis
                foundDiagnosis = true;
                assertEquals("Asthma respiratory disease", entry.getValue(), "Patient 2 diagnosis should be latest: Asthma respiratory disease");
            }
        }

        assertTrue(foundAge, "Patient 2 should have age data entry");
        assertTrue(foundDiagnosis, "Patient 2 should have diagnosis data entry");
    }


    /**
     * Creates a comprehensive PatientDataDTO with all data types for testing
     * TODO: FILE type validation/normalization not yet implemented
     */
    public static PatientDTO createNewComprehensiveTestPatientDTO() {
        PatientDTO patient = new PatientDTO();
        patient.setCohortId(TEST_COHORT_ID);
        patient.setExternalPatientId(COMPREHENSIVE_PATIENT_EXTERNAL_ID);

        Set<PatientDataEntryDTO> dataEntries = new HashSet<>();

        // String field
        PatientDataEntryDTO stringEntry = new PatientDataEntryDTO();
        stringEntry.setSchemaNodeId(COMPREHENSIVE_TEST_STRING_NODE_ID);
        stringEntry.setValue("Test String Value");
        dataEntries.add(stringEntry);

        // Integer field
        PatientDataEntryDTO intEntry = new PatientDataEntryDTO();
        intEntry.setSchemaNodeId(COMPREHENSIVE_TEST_INT_NODE_ID);
        intEntry.setValue(42);
        dataEntries.add(intEntry);

        // Float field
        PatientDataEntryDTO floatEntry = new PatientDataEntryDTO();
        floatEntry.setSchemaNodeId(COMPREHENSIVE_TEST_FLOAT_NODE_ID);
        floatEntry.setValue(7.17f);
        dataEntries.add(floatEntry);

        // Boolean field
        PatientDataEntryDTO booleanEntry = new PatientDataEntryDTO();
        booleanEntry.setSchemaNodeId(COMPREHENSIVE_TEST_BOOLEAN_NODE_ID);
        booleanEntry.setValue(true);
        dataEntries.add(booleanEntry);

        // File field - TODO: FILE type not yet implemented
        // PatientDataEntryDTO fileEntry = new PatientDataEntryDTO();
        // fileEntry.setSchemaNodeId(COMPREHENSIVE_TEST_FILE_NODE_ID);
        // fileEntry.setValue("test-file-content.txt");
        // dataEntries.add(fileEntry);

        // Date field (ISO format)
        PatientDataEntryDTO dateEntry = new PatientDataEntryDTO();
        dateEntry.setSchemaNodeId(COMPREHENSIVE_TEST_DATE_NODE_ID);
        dateEntry.setValue("2023-07-22");
        dataEntries.add(dateEntry);

        // DateTime field (ISO format)
        PatientDataEntryDTO dateTimeEntry = new PatientDataEntryDTO();
        dateTimeEntry.setSchemaNodeId(COMPREHENSIVE_TEST_DATETIME_NODE_ID);
        dateTimeEntry.setValue("2023-07-22T10:15:30Z");
        dataEntries.add(dateTimeEntry);

        // Categorical field
        PatientDataEntryDTO categoricalEntry = new PatientDataEntryDTO();
        categoricalEntry.setSchemaNodeId(COMPREHENSIVE_TEST_CATEGORICAL_NODE_ID);
        categoricalEntry.setValue("Diabetes Type 1");
        dataEntries.add(categoricalEntry);

        patient.setDataEntries(dataEntries);
        return patient;
    }

    public static PatientDTO updateComprehensiveTestPatientDTOAfterCreation(PatientDTO originalPatient) {
        PatientDTO updatedPatient = new PatientDTO();
        updatedPatient.setId(originalPatient.getId());
        updatedPatient.setVersion(originalPatient.getVersion());
        updatedPatient.setCohortId(originalPatient.getCohortId());
        updatedPatient.setExternalPatientId(originalPatient.getExternalPatientId());

        Set<PatientDataEntryDTO> updatedDataEntries = new HashSet<>();

        // Update all data entries with new values
        for (PatientDataEntryDTO originalEntry : originalPatient.getDataEntries()) {
            PatientDataEntryDTO updatedEntry = new PatientDataEntryDTO();
            updatedEntry.setId(originalEntry.getId());
            updatedEntry.setVersion(originalEntry.getVersion());
            updatedEntry.setSchemaNodeId(originalEntry.getSchemaNodeId());

            // Update values based on schema node ID
            if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_STRING_NODE_ID)) {
                updatedEntry.setValue("Updated String Value");
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_INT_NODE_ID)) {
                updatedEntry.setValue(99);
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FLOAT_NODE_ID)) {
                updatedEntry.setValue(13.71f);
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_BOOLEAN_NODE_ID)) {
                updatedEntry.setValue(false);
                // FILE type not yet implemented
                // } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FILE_NODE_ID)) {
                //     updatedEntry.setValue("updated-file-content.txt");
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATE_NODE_ID)) {
                updatedEntry.setValue("2024-01-01");
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATETIME_NODE_ID)) {
                updatedEntry.setValue("2024-01-01T12:00:00Z");
            } else if (originalEntry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_CATEGORICAL_NODE_ID)) {
                updatedEntry.setValue("Diabetes Type 2");
            }

            updatedDataEntries.add(updatedEntry);
        }

        updatedPatient.setDataEntries(updatedDataEntries);
        return updatedPatient;
    }

    public static void verifyAllDataTypesPresentComprehensivePatient(PatientDTO patient) {
        assertEquals(7, patient.getDataEntries().size(), "Should have 7 data entries for all data types");

        boolean hasString = false, hasInt = false, hasFloat = false, hasBoolean = false;
        boolean hasDate = false, hasDateTime = false, hasCategorical = false;
        // TODO: hasFile = false; // FILE type not yet implemented

        for (PatientDataEntryDTO entry : patient.getDataEntries()) {
            if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_STRING_NODE_ID)) {
                hasString = true;
                assertTrue(entry.getValue() instanceof String, "String value should be String type");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_INT_NODE_ID)) {
                hasInt = true;
                assertTrue(entry.getValue() instanceof Number, "Int value should be Number type");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FLOAT_NODE_ID)) {
                hasFloat = true;
                assertTrue(entry.getValue() instanceof Number, "Float value should be Number type");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_BOOLEAN_NODE_ID)) {
                hasBoolean = true;
                assertTrue(entry.getValue() instanceof Boolean, "Boolean value should be Boolean type");
                // FILE type not yet implemented
                // } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FILE_NODE_ID)) {
                //     hasFile = true;
                //     assertTrue(entry.getValue() instanceof String, "File value should be String type");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATE_NODE_ID)) {
                hasDate = true;
                assertNotNull(entry.getValue(), "Date value should not be null");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATETIME_NODE_ID)) {
                hasDateTime = true;
                assertNotNull(entry.getValue(), "DateTime value should not be null");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_CATEGORICAL_NODE_ID)) {
                hasCategorical = true;
                assertTrue(entry.getValue() instanceof String, "Categorical value should be String type");
            }
        }

        assertTrue(hasString, "Should have string data entry");
        assertTrue(hasInt, "Should have int data entry");
        assertTrue(hasFloat, "Should have float data entry");
        assertTrue(hasBoolean, "Should have boolean data entry");
        // assertTrue(hasFile, "Should have file data entry"); // TODO: FILE type not yet implemented
        assertTrue(hasDate, "Should have date data entry");
        assertTrue(hasDateTime, "Should have datetime data entry");
        assertTrue(hasCategorical, "Should have categorical data entry");
    }

    public static void verifyAllDataTypesUpdatedComprehensivePatient(PatientDTO patient) {
        assertEquals(7, patient.getDataEntries().size(), "Should have 7 data entries for all data types");

        for (PatientDataEntryDTO entry : patient.getDataEntries()) {
            if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_STRING_NODE_ID)) {
                assertEquals("Updated String Value", entry.getValue(), "String should be updated");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_INT_NODE_ID)) {
                assertEquals(99L, Long.parseLong(entry.getValue().toString()), "Int should be updated");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FLOAT_NODE_ID)) {
                assertEquals(13.71f, Float.parseFloat(entry.getValue().toString()), "Float should be updated");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_BOOLEAN_NODE_ID)) {
                assertEquals(false, entry.getValue(), "Boolean should be updated");
                // FILE type not yet implemented
                // } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_FILE_NODE_ID)) {
                //     assertEquals("updated-file-content.txt", entry.getValue(), "File should be updated");
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATE_NODE_ID)) {
                // For dates, we need to compare the actual stored value
                assertNotNull(entry.getValue(), "Date should not be null");
                // The exact format might vary based on how it's stored/retrieved
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_DATETIME_NODE_ID)) {
                // For datetime, we need to compare the actual stored value
                assertNotNull(entry.getValue(), "DateTime should not be null");
                // The exact format might vary based on how it's stored/retrieved
            } else if (entry.getSchemaNodeId().equals(COMPREHENSIVE_TEST_CATEGORICAL_NODE_ID)) {
                assertEquals("Diabetes Type 2", entry.getValue(), "Categorical should be updated");
            }
        }
    }
}
