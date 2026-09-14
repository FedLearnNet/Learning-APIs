package rest;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientService;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryService;
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

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class PatientDataEntryServiceTest {
    @Inject
    UserTransaction userTransaction;

    @TestHTTPResource(AuditService.PATH)
    String auditServiceUrl;

    @TestHTTPResource(PatientDataEntryService.PATH + "/bulk")
    String patientDataEntryServiceUrl;

    @TestHTTPResource(PatientService.PATH)
    String patientMetaServicePath;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void addDataEntries_validData_returnsCreatedAndLogsAudit() throws Exception {
        try {
            PatientDTO comprehensivePatient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
            List<PatientDataEntryDTO> newEntries = new java.util.ArrayList<>(comprehensivePatient.getDataEntries());
            comprehensivePatient.setDataEntries(null); // Clear data entries for creation
            userTransaction.begin();
            PatientDTO createdPatient = createTestPatientWithExternalId(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID + "_POST_TEST_VALID", PatientDataTestResource.TEST_COHORT_ID);
            userTransaction.commit();

            assertTrue(createdPatient.getCohortId() == PatientDataTestResource.TEST_COHORT_ID, "Created patient should belong to the test cohort");

            // Act: POST to add data entries
            userTransaction.begin();
            List<PatientDataEntryDTO> createdEntries = given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .pathParam("internalPatientId", createdPatient.getId())
                    .contentType(ContentType.JSON)
                    .body(newEntries)
                    .when()
                    .post(patientDataEntryServiceUrl)
                    .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath()
                    .getList(".", PatientDataEntryDTO.class);
            userTransaction.commit();
            // Assert: returned entries have IDs
            assertTrue(createdEntries.stream().allMatch(e -> e.getId() != null), "All created entries should have IDs");
            assertTrue(createdEntries.size() == newEntries.size(), "All created entries should match the input size");

            // Query AuditService to ensure MOD entry for patient and ADD entries for data entries
            PagedResponse<PatientDataTraceabilityLogDto> auditLogs = given()
                    .queryParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .queryParam("page", 0)
                    .queryParam("size", 100)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", createdPatient.getExternalPatientId())
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServiceUrl)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new io.restassured.common.mapper.TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });

            assertTrue(auditLogs.getResults().size() == 2, "Should have 2 audit logs: one for patient creation and one for data entries addition");
            PatientDataTraceabilityLogDto updateLog = auditLogs.getResults().get(0);
            assertTrue(RevisionType.MOD.equals(updateLog.getChangeType()), "First log should be a modification");
            assertTrue("admin".equals(updateLog.getUserId()), "First log should be created by admin");
            PatientDataTraceabilityLogDto addLog = auditLogs.getResults().get(1);
            assertTrue(RevisionType.ADD.equals(addLog.getChangeType()), "Second log should be an addition");
            assertTrue("admin".equals(addLog.getUserId()), "Second log should be created by admin");

            PagedResponse<PatientDataTraceabilityLogDto> pagedAuditLogs = given()
                    .queryParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .queryParam("page", 0)
                    .queryParam("size", 1)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", createdPatient.getExternalPatientId())
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServiceUrl)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });

            assertTrue(pagedAuditLogs.getResults().size() == 1, "Paged audit logs should return only one result");
            assertTrue(pagedAuditLogs.getTotalCount() == 2, "Paged audit logs should keep total count of filtered entries");
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
    void addDataEntries_invalidData_returnsBadRequest() {
        Long cohortId = PatientDataTestResource.TEST_COHORT_ID;
        PatientDTO patient = createTestPatientWithExternalId(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID + "_POST_TEST_INVALID", cohortId);
        List<PatientDataEntryDTO> newEntries = new ArrayList<>(patient.getDataEntries());

        // 1. Giving an ID in the input
        for (int i = 0; i < newEntries.size(); i++) {
            newEntries.get(i).setId((long) i);
        }
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(newEntries)
                .when()
                .post(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        newEntries.forEach(entry -> entry.setId(null)); // Reset IDs for next tests

        // 2. Not giving any schemanode
        List<Long> tmpSchemaNodes = newEntries.stream()
                .map(PatientDataEntryDTO::getSchemaNodeId)
                .toList();
        newEntries.forEach(entry -> entry.setSchemaNodeId(null)); // Set all schema nodes
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(newEntries)
                .when()
                .post(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        newEntries.forEach(entry -> entry.setSchemaNodeId(tmpSchemaNodes.get(newEntries.indexOf(entry)))); // Reset schema nodes for next tests

        // 3. Giving a schemanode not in the corresponding schema of the cohort
        // The used schema is a bit later, so we use small schema nodes (2 -> Age)
        newEntries.forEach(entry -> entry.setSchemaNodeId(2L));
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(newEntries)
                .when()
                .post(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        newEntries.forEach(entry -> entry.setSchemaNodeId(tmpSchemaNodes.get(newEntries.indexOf(entry)))); // Reset schema nodes for next tests
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void updateDataEntries_validData_returnsUpdatedAndLogsAudit() throws Exception {
        try {
            userTransaction.begin();
            PatientDTO createdPatient = createTestPatientWithExternalId(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID + "_PUT_TEST_VALID", PatientDataTestResource.TEST_COHORT_ID);

            assertTrue(createdPatient.getCohortId() == PatientDataTestResource.TEST_COHORT_ID, "Created patient should belong to the test cohort");
            userTransaction.commit(); // CREATE entry

            // Update the entries
            PatientDTO updatedPatient = PatientDataTestResource.updateComprehensiveTestPatientDTOAfterCreation(createdPatient);

            // Actual test
            userTransaction.begin();
            List<PatientDataEntryDTO> updatedResult = given()
                    .pathParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .pathParam("internalPatientId", createdPatient.getId())
                    .contentType(ContentType.JSON)
                    .body(updatedPatient.getDataEntries())
                    .when()
                    .put(patientDataEntryServiceUrl)
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath()
                    .getList(".", PatientDataEntryDTO.class);
            updatedPatient.setDataEntries(new java.util.HashSet<>(updatedResult));
            userTransaction.commit(); // MOD entry
            assertTrue(updatedResult.size() == updatedPatient.getDataEntries().size(), "Updated entries should match the input size");
            PatientDataTestResource.verifyAllDataTypesPresentComprehensivePatient(updatedPatient);
            PatientDataTestResource.verifyAllDataTypesUpdatedComprehensivePatient(updatedPatient);

            // Check AuditService for update logs
            PagedResponse<PatientDataTraceabilityLogDto> auditLogs = given()
                    .queryParam("cohortId", PatientDataTestResource.TEST_COHORT_ID)
                    .queryParam("page", 0)
                    .queryParam("size", 100)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", createdPatient.getExternalPatientId())
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServiceUrl)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });

            assertTrue(auditLogs.getResults().size() == 2, "Should have 2 audit logs: one for patient creation and one for data entries update");
            PatientDataTraceabilityLogDto updateLog = auditLogs.getResults().get(0);
            assertTrue(RevisionType.MOD.equals(updateLog.getChangeType()), "First log should be a modification");
            assertTrue("admin".equals(updateLog.getUserId()), "First log should be created by admin");
            PatientDataTraceabilityLogDto addLog = auditLogs.getResults().get(1);
            assertTrue(RevisionType.ADD.equals(addLog.getChangeType()), "Second log should be an addition");
            assertTrue("admin".equals(addLog.getUserId()), "Second log should be created by admin");
        } finally {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.commit();
            } else if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    @TestTransaction
    void updateDataEntries_invalidData_returnsBadRequest() {
        Long cohortId = PatientDataTestResource.TEST_COHORT_ID;
        PatientDTO patient = createTestPatientWithExternalId(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID + "_PUT_TEST_INVALID", cohortId);
        List<PatientDataEntryDTO> updateEntries = new ArrayList<>(patient.getDataEntries());

        // 1. Not giving any value
        List<Object> tmpValues = updateEntries.stream()
                .map(PatientDataEntryDTO::getValue)
                .toList();
        updateEntries.forEach(entry -> entry.setValue(null)); // Set all values to null
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(updateEntries)
                .when()
                .put(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        updateEntries.forEach(entry -> entry.setValue(tmpValues.get(updateEntries.indexOf(entry)))); // Reset values for next tests

        // 2. Not giving any schemanode
        List<Long> tmpSchemaNodes = updateEntries.stream()
                .map(PatientDataEntryDTO::getSchemaNodeId)
                .toList();
        updateEntries.forEach(entry -> entry.setSchemaNodeId(null)); // Set all schema nodes
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(updateEntries)
                .when()
                .put(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        updateEntries.forEach(entry -> entry.setSchemaNodeId(tmpSchemaNodes.get(updateEntries.indexOf(entry)))); // Reset schema nodes for next tests

        // 3. Giving a schemanode not in the corresponding schema of the cohort
        // The used schema is a bit later, so we use small schema nodes (2 -> Age)
        updateEntries.forEach(entry -> entry.setSchemaNodeId(2L));
        given()
                .pathParam("cohortId", cohortId)
                .pathParam("internalPatientId", patient.getId())
                .contentType(ContentType.JSON)
                .body(updateEntries)
                .when()
                .put(patientDataEntryServiceUrl)
                .then()
                .statusCode(400);
        updateEntries.forEach(entry -> entry.setSchemaNodeId(tmpSchemaNodes.get(updateEntries.indexOf(entry)))); // Reset schema nodes for next tests
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void deleteDataEntries_validData_returnsOK() throws Exception {
        try {
            // Create a patient
            Long cohortId = PatientDataTestResource.TEST_COHORT_ID;
            PatientDTO patient = createTestPatientWithExternalId(PatientDataTestResource.COMPREHENSIVE_PATIENT_EXTERNAL_ID + "_DELETE_TEST_VALID", cohortId);

            List<PatientDataEntryDTO> deleteEntries = new ArrayList<>(patient.getDataEntries());
            List<Long> tmpIds = deleteEntries.stream()
                    .map(PatientDataEntryDTO::getId)
                    .toList();

            // 1. Deleting all entries
            userTransaction.begin();
            given()
                    .pathParam("cohortId", cohortId)
                    .pathParam("internalPatientId", patient.getId())
                    .contentType(ContentType.JSON)
                    .body(tmpIds)
                    .when()
                    .delete(patientDataEntryServiceUrl)
                    .then()
                    .statusCode(200);
            userTransaction.commit();

            // Get the current entries of the patient
            PatientDTO updatedPatient = given()
                    .pathParam("cohortId", cohortId)
                    .pathParam("internalPatientId", patient.getId())
                    .when()
                    .get(patientMetaServicePath + "/{internalPatientId}")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(PatientDTO.class);
            assertTrue(updatedPatient.getDataEntries().isEmpty(), "All data entries should be deleted");

            // Check audit logs
            PagedResponse<PatientDataTraceabilityLogDto> auditLogs = given()
                    .queryParam("cohortId", cohortId)
                    .queryParam("page", 0)
                    .queryParam("size", 100)
                    .queryParam("sort", AuditFieldEnum.REVISION_TIMESTAMP)
                    .queryParam("direction", SortDirectionEnum.DESC)
                    .queryParam("search_terms", patient.getExternalPatientId())
                    .queryParam("search_fields", AuditFieldEnum.EXTERNAL_PATIENT_ID)
                    .when()
                    .get(auditServiceUrl)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PagedResponse<PatientDataTraceabilityLogDto>>() {
                    });
            assertTrue(auditLogs.getResults().size() == 2, "Should have 2 audit logs: one for patient creation and one for data entries deletion");
            PatientDataTraceabilityLogDto deleteLog = auditLogs.getResults().get(0);
            assertTrue(RevisionType.MOD.equals(deleteLog.getChangeType()), "First log should be a modification");
            assertTrue("admin".equals(deleteLog.getUserId()), "First log should be created by admin");
            PatientDataTraceabilityLogDto addLog = auditLogs.getResults().get(1);
            assertTrue(RevisionType.ADD.equals(addLog.getChangeType()), "Second log should be an addition");
            assertTrue("admin".equals(addLog.getUserId()), "Second log should be created by admin");


            // 2. Deleting no entries
            List<Long> nonExistentIds = List.of(999L, 998L);
            given()
                    .pathParam("cohortId", cohortId)
                    .pathParam("internalPatientId", patient.getId())
                    .contentType(ContentType.JSON)
                    .body(nonExistentIds)
                    .when()
                    .delete(patientDataEntryServiceUrl)
                    .then()
                    .statusCode(200);

        } finally {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.commit();
            } else if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
        }
    }

    private PatientDTO createTestPatientWithExternalId(String externalPatientId, Long cohortId) {
        PatientDTO patient = PatientDataTestResource.createNewComprehensiveTestPatientDTO();
        patient.setExternalPatientId(externalPatientId);
        patient.setId(null); // Ensure we create a new patient
        return given()
                .pathParam("cohortId", cohortId)
                .contentType(ContentType.JSON)
                .body(patient)
                .when()
                .post(patientMetaServicePath)
                .then()
                .statusCode(201)
                .extract()
                .as(PatientDTO.class);
    }
}
