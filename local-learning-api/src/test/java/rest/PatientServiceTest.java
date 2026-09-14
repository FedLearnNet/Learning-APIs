package rest;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientService;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditFieldEnum;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditService;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.PatientDataTraceabilityLogDto;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rest.helper.RestAssuredConfigUtil;
import rest.resource.PatientDataTestResource;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PatientServiceTest {
    @Inject
    UserTransaction userTransaction;

    @TestHTTPResource(AuditService.PATH)
    String auditServicePath;

    @TestHTTPResource(PatientService.PATH)
    String patientMetaServicePath;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getReducedPatientData_validParams_returnsOk() {
        // Arrange - Based on test data from SQL
        Long cohortId = 1L; // COHORT_001 has patients 1 and 2
        String numericReduction = "average";
        String nonNumericReduction = "latest";
        Integer page = 1;
        Integer pageSize = 50;

        // Create expected PatientDataDTO based on SQL test data for cohort 1
        // Patient 1 has:
        // - Age (schemaNodeId=2): values 25, 26, 27 -> average = 26
        // - Diagnosis (schemaNodeId=3): "Diabetes Type 2", "Hypertension" -> latest = "Hypertension"
        PatientDTO patient1 = new PatientDTO();
        patient1.setId(1L);
        patient1.setCohortId(1L);
        patient1.setExternalPatientId("PATIENT_001");

        // Create expected data entries for patient 1
        PatientDataEntryDTO patient1Age = new PatientDataEntryDTO();
        patient1Age.setSchemaNodeId(2L);
        patient1Age.setValue(26); // Average of 25, 26, 27

        PatientDataEntryDTO patient1Diagnosis = new PatientDataEntryDTO();
        patient1Diagnosis.setSchemaNodeId(3L);
        patient1Diagnosis.setValue("Hypertension"); // Latest

        patient1.setDataEntries(Set.of(patient1Age, patient1Diagnosis));

        // Patient 2 has:
        // - Age (schemaNodeId=2): values 30, 31 -> average = 30.5
        // - Diagnosis (schemaNodeId=3): "Asthma respiratory disease" -> latest = "Asthma respiratory disease"
        PatientDTO patient2 = new PatientDTO();
        patient2.setId(2L);
        patient2.setCohortId(1L);
        patient2.setExternalPatientId("PATIENT_002");

        // Create expected data entries for patient 2
        PatientDataEntryDTO patient2Age = new PatientDataEntryDTO();
        patient2Age.setSchemaNodeId(2L);
        patient2Age.setValue(30.5); // Average of 30, 31

        PatientDataEntryDTO patient2Diagnosis = new PatientDataEntryDTO();
        patient2Diagnosis.setSchemaNodeId(3L);
        patient2Diagnosis.setValue("Asthma respiratory disease"); // Latest

        patient2.setDataEntries(Set.of(patient2Age, patient2Diagnosis));

        Set<PatientDTO> expectedSet = Set.of(patient1, patient2);

        // Act
        PagedResponse<PatientDTO> response = given()
                .pathParam("cohortId", cohortId)
                .queryParam("numericReduction", numericReduction)
                .queryParam("nonNumericReduction", nonNumericReduction)
                .queryParam("page", page)
                .queryParam("page_size", pageSize)
                .when()
                .get(patientMetaServicePath + "/reduced")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(new TypeRef<PagedResponse<PatientDTO>>() {
                });

        // Assert
        assertNotNull(response);

        // Verify pagination metadata
        assertEquals(page, response.getPage(), "Page should match requested page");
        assertEquals(pageSize, response.getPageSize(), "Page size should match requested page size");
        assertTrue(response.getTotalCount() >= 0, "Total count should be non-negative");

        // Get the results list
        List<PatientDTO> list = response.getResults();

        // Verify each element is a PatientDataDTO and check basic fields
        assertEquals(expectedSet.size(), list.size(), "Results size should match expected set size");

        for (PatientDTO patientData : list) {
            // Check that this patient exists in our expected set by ID
            boolean foundMatchingPatient = false;
            for (PatientDTO expected : expectedSet) {
                if (patientData.getId().equals(expected.getId())) {
                    foundMatchingPatient = true;

                    // Verify basic patient fields
                    assertEquals(expected.getCohortId(), patientData.getCohortId(),
                            "Cohort ID should match for patient " + patientData.getId());
                    assertEquals(expected.getExternalPatientId(), patientData.getExternalPatientId(),
                            "External patient ID should match for patient " + patientData.getId());

                    // Verify data entries exist and have correct structure
                    assertNotNull(patientData.getDataEntries(), "Data entries should not be null");
                    assertTrue(patientData.getDataEntries().size() > 0, "Should have at least one data entry");

                    // Check specific reduced values based on SQL data
                    if (patientData.getId().equals(1L)) {
                        // Patient 1 should have age average=26 and diagnosis="Hypertension"
                        PatientDataTestResource.verifyPatient1DataReduced(patientData);
                    } else if (patientData.getId().equals(2L)) {
                        // Patient 2 should have age average=30.5 and diagnosis="Asthma respiratory disease"
                        PatientDataTestResource.verifyPatient2DataReduced(patientData);
                    }

                    break;
                }
            }

            assertTrue(foundMatchingPatient,
                    "Patient should be in expected set: " + patientData.getId());
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getReducedPatientData_invalidPage_returnsBadRequest() {
        // Arrange
        Long cohortId = 1L;
        String numericReduction = "average";
        String nonNumericReduction = "latest";
        Integer page = 0; // Invalid page
        Integer pageSize = 50;

        // Act
        given()
                .pathParam("cohortId", cohortId)
                .queryParam("numericReduction", numericReduction)
                .queryParam("nonNumericReduction", nonNumericReduction)
                .queryParam("page", page)
                .queryParam("page_size", pageSize)
                .when()
                .get(patientMetaServicePath + "/reduced")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getReducedPatientData_invalidPageSize_returnsBadRequest() {
        // Arrange
        Long cohortId = 1L;
        String numericReduction = "average";
        String nonNumericReduction = "latest";
        Integer page = 1;
        Integer pageSize = 0; // Invalid page size

        // Act
        given()
                .pathParam("cohortId", cohortId)
                .queryParam("numericReduction", numericReduction)
                .queryParam("nonNumericReduction", nonNumericReduction)
                .queryParam("page", page)
                .queryParam("page_size", pageSize)
                .when()
                .get(patientMetaServicePath + "/reduced")
                .then()
                .statusCode(400);
    }

    @Test
    void testEndpointsWithoutAuthentication_returns401() {
        // Test GET /reduced without authentication
        given()
                .pathParam("cohortId", 1L)
                .queryParam("numericReduction", "average")
                .queryParam("nonNumericReduction", "latest")
                .queryParam("page", 1)
                .queryParam("page_size", 50)
                .when()
                .get(patientMetaServicePath + "/reduced")
                .then()
                .statusCode(401);

        // Test GET / without authentication
        given()
                .pathParam("cohortId", 1L)
                .when()
                .get(patientMetaServicePath)
                .then()
                .statusCode(401);

        // Test GET /{id} without authentication
        given()
                .pathParam("cohortId", 1L)
                .when()
                .get(patientMetaServicePath + "/1")
                .then()
                .statusCode(401);

        // Test POST without authentication
        PatientDTO newPatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .body(newPatient)
                .when()
                .post(patientMetaServicePath)
                .then()
                .statusCode(401);

        // Test DELETE without authentication
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .when()
                .delete(patientMetaServicePath + "/1")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getPatientDataById_validId_returnsOk() {
        Long invalidPatientId = 999L;
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .when()
                .get(patientMetaServicePath + "/" + invalidPatientId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getPatientDataById_invalidId_returnsNotFound() {
        // Arrange
        Long invalidPatientId = 999L;

        // Act & Assert
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .when()
                .get(patientMetaServicePath + "/" + invalidPatientId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listPatientData_validCohort_returnsOk() {
        // Arrange - Use the test cohort (initially empty)
        Long cohortId = 1L;

        // Act
        List<PatientDTO> result = given()
                .pathParam("cohortId", cohortId)
                .when()
                .get(patientMetaServicePath + "/reduced")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath()
                .getList("results", PatientDTO.class);

        // Assert
        assertNotNull(result);
        assertTrue(result.size() == 2); // Should have exactly patients 1 and 2 from test data

        // Verify that all returned patients belong to the cohort
        for (PatientDTO patient : result) {
            assertEquals(cohortId, patient.getCohortId());
            assertNotNull(patient.getExternalPatientId());
            assertNotNull(patient.getDataEntries());
        }
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    void createPatient_duplicateExternalId_returnsConflict() {
        // Arrange - First create a patient
        PatientDTO firstPatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
        firstPatient.setExternalPatientId("DUPLICATE_TEST_PATIENT");

        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .body(firstPatient)
                .when()
                .post(patientMetaServicePath)
                .then()
                .statusCode(201);

        // Now try to create another patient with the same external ID
        PatientDTO duplicatePatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
        duplicatePatient.setExternalPatientId("DUPLICATE_TEST_PATIENT"); // Same external ID

        // Act
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .body(duplicatePatient)
                .when()
                .post(patientMetaServicePath)
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void deletePatientData_validId_returnsNoContentAndLogsAudit() throws Exception {
        try {
            // Start transaction for patient creation and deletion
            userTransaction.begin();

            // Arrange - First create a patient to delete
            PatientDTO newPatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
            newPatient.setExternalPatientId("TEST_PATIENT_DELETE");

            PatientDTO createdPatient = given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .contentType(ContentType.JSON)
                    .body(newPatient)
                    .when()
                    .post(patientMetaServicePath)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(PatientDTO.class);

            Long patientId = createdPatient.getId();

            // Act
            given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .when()
                    .delete(patientMetaServicePath + "/" + patientId)
                    .then()
                    .statusCode(200);

            // Commit the transaction to trigger audit listeners
            userTransaction.commit();

            // Get the patient and ensure it is soft-deleted
            PatientDTO deletedPatient = given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .when()
                    .get(patientMetaServicePath + "/" + patientId)
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(PatientDTO.class);
            assertNotNull(deletedPatient, "Deleted patient should still exist in the system");
            assertTrue(deletedPatient.getDataEntries().isEmpty(), "Deleted patient data entries should be empty after soft delete");

            // Verify audit logging - search for entries related to the deleted patient
            PagedResponse<PatientDataTraceabilityLogDto> auditLogs = given()
                    .queryParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .queryParam("page", 0)
                    .queryParam("size", 100)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", "TEST_PATIENT_DELETE")
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServicePath)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });

            assertTrue(auditLogs.getResults().size() == 2, "2 Audit logs should exist for deleted patient");
            PatientDataTraceabilityLogDto auditLogDel = auditLogs.getResults().get(0);
            PatientDataTraceabilityLogDto auditLogCreate = auditLogs.getResults().get(1);
            assertEquals(RevisionType.MOD, auditLogDel.getChangeType(), "Change type should be MOD for patient deletion");
            // It's just a soft delete, it will be logged as MOD and all data entries should be empty
            assertEquals("admin", auditLogDel.getUserId(), "Keycloak user ID should be admin");
            assertEquals("TEST_PATIENT_DELETE", auditLogDel.getPatientId(), "External patient ID should match");
            assertEquals(PatientDataTestResource.TEST_COHORT_ID, auditLogDel.getCohortId(), "Cohort ID should match the deleted patient cohort");
            assertEquals(RevisionType.ADD, auditLogCreate.getChangeType(), "Change type should be ADD for patient creation");
            assertEquals("admin", auditLogCreate.getUserId(), "Keycloak user ID should be admin");
            assertEquals("TEST_PATIENT_DELETE", auditLogCreate.getPatientId(), "External patient ID should match");
            assertEquals(PatientDataTestResource.TEST_COHORT_ID, auditLogCreate.getCohortId(), "Cohort ID should match the created patient cohort");
        } finally {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.commit();
            } else if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
        }
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    void deletePatientData_invalidId_returnsOk() {
        // Arrange
        Long invalidPatientId = 999L;

        // Act
        given()
                .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                .when()
                .delete(patientMetaServicePath + "/" + invalidPatientId)
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createPatient_validData_returnsCreatedAndLogsAudit() throws Exception {
        try {
            // Start transaction for patient creation
            userTransaction.begin();

            // Arrange - Use comprehensive test data with all data types
            PatientDTO newPatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();

            // Act
            PatientDTO response = given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .contentType(ContentType.JSON)
                    .body(newPatient)
                    .when()
                    .post(patientMetaServicePath)
                    .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(PatientDTO.class);

            // Assert
            assertNotNull(response.getId());
            assertEquals(PatientDataTestResource.TEST_COHORT_ID, response.getCohortId());
            assertEquals(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID, response.getExternalPatientId());
            assertEquals(7, response.getDataEntries().size(), "Should have 7 data entries for all data types");
            // TODO: change this to 8 when the file type is implemented
            // Verify all data types are present and correctly stored
            PatientDataTestResource.verifyAllDataTypesPresentComprehensivePatient(response);

            // Commit the transaction to trigger audit listeners
            userTransaction.commit();

            // Verify audit logging - search for new entries related to this patient
            PagedResponse<PatientDataTraceabilityLogDto> finalAuditLogs = given()
                    .queryParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .queryParam("page", 0)
                    .queryParam("size", 1000)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID)
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServicePath)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });

            // Should have audit entries for the created patient
            assertTrue(finalAuditLogs.getResults().size() == 1, "Audit logs should exist for newly created patient");

            // Verify the audit log details
            PatientDataTraceabilityLogDto auditLog = finalAuditLogs.getResults().get(0);
            assertEquals(RevisionType.ADD, auditLog.getChangeType(), "Change type should be ADD for patient creation");
            assertEquals("admin", auditLog.getUserId(), "Keycloak user ID should be admin");
            assertEquals(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID, auditLog.getPatientId(), "External patient ID should match");
            assertEquals(PatientDataTestResource.TEST_COHORT_ID, auditLog.getCohortId(), "Cohort ID should match the created patient cohort");
        } finally {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.commit();
            } else if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
        }
    }
}
