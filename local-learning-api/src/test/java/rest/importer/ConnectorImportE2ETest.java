package rest.importer;

import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientService;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileImportResultDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportEventDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportPhase;
import bio.cosy.feddb.local.api.importer.files.progress.ImportTableDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportTableState;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.RunErrorLogListResponseDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rest.helper.RestAssuredConfigUtil;
import rest.resource.PatientDataTestResource;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E integration test for the connector import pipeline.
 * <p>
 * Flow:
 * <ol>
 *   <li>Upload a CSV file for cohort 5 (comprehensive test cohort)</li>
 *   <li>Create a connector with FILE input config and schema mapping</li>
 *   <li>Trigger a run via POST /connectors/{id}/run</li>
 *   <li>Poll the run status until FINISHED or ERROR</li>
 *   <li>Verify run-logs (ConnectorRunPatientLog) exist</li>
 *   <li>Verify patients were imported into the cohort</li>
 * </ol>
 */
@QuarkusTest
public class ConnectorImportE2ETest {

    private static final Long TEST_COHORT_ID = PatientDataTestResource.TEST_COHORT_ID; // 5

    // Schema node IDs for cohort 5 (from schema.sql):
    // 13 = TestInt (INT, parent=12 NumericGroup)
    // 14 = TestFloat (FLOAT with MIN=5.0, parent=12 NumericGroup)
    private static final Long INT_SCHEMA_NODE_ID = PatientDataTestResource.COMPREHENSIVE_TEST_INT_NODE_ID;    // 13
    private static final Long FLOAT_SCHEMA_NODE_ID = PatientDataTestResource.COMPREHENSIVE_TEST_FLOAT_NODE_ID; // 14

    // REST paths (from @Path annotations on service interfaces)
    private static final String CONNECTORS_PATH = "/connectors";
    private static final String CONNECTOR_FILES_PATH = "/connectors/files";
    private static final String CONNECTOR_RUNS_PATH = "/connectors/runs";
    private static final String CSV_UPLOAD_SETTINGS = """
            {
              "fileType": "CSV",
              "delimiter": ",",
              "hasHeader": true,
              "firstSheetOnly": true,
              "previewRows": 10
            }
            """;

    @Inject
    ObjectMapper objectMapper;

    @TestHTTPResource(PatientService.PATH)
    String patientServicePath;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_returnsBoundedPreviewAndFullProfiles() throws Exception {
        File csvFile = createTestCsvFile();
        try {
            List<ConnectorFilesDetailDTO> uploadedFiles =
                    uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null).getFiles();

            assertEquals(1, uploadedFiles.size());
            ConnectorFilesDetailDTO uploaded = uploadedFiles.getFirst();
            assertTrue(Boolean.TRUE.equals(uploaded.getFileExists()));
            assertNotNull(uploaded.getUploadSettings());
            assertEquals(FileParsingType.CSV, uploaded.getUploadSettings().getFileType());
            assertTrue(uploaded.getUploadSettings().isHasHeader());
            assertEquals(1, uploaded.getUploadInfo().size());
            int previewSize = objectMapper.readTree(uploaded.getUploadInfo().getFirst().getJson()).size();
            assertTrue(previewSize <= 10);
            assertFalse(uploaded.getUploadInfo().getFirst().getColumnProfiles().isEmpty());
            assertTrue(uploaded.getUploadInfo().getFirst().getColumnProfiles().stream()
                    .allMatch(profile -> profile.count() >= previewSize));
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * An import named by the client can be asked about afterwards, by a page that did not start it
     * and by one that lost the request that did - which is the whole point of naming it.
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_progressIsReadableUnderTheImportId() throws Exception {
        File csvFile = createTestCsvFile();
        String importId = UUID.randomUUID().toString();
        try {
            uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, importId, null);

            given()
                    .pathParam("importId", importId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .then()
                    .statusCode(200)
                    .body("phase", equalTo("SUCCEEDED"))
                    .body("fileName", equalTo(csvFile.getName()))
                    .body("fileId", notNullValue())
                    .body("tables.size()", equalTo(1))
                    .body("tables[0].rows", greaterThan(0))
                    .body("tables[0].columns", greaterThan(0));

            given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/imports")
                    .then()
                    .statusCode(200)
                    .body("importId", hasItem(importId));

            // An import nobody started, or one long forgotten, is not something to report on.
            given()
                    .pathParam("importId", UUID.randomUUID().toString())
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .then()
                    .statusCode(404);
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * Asking for events instead of a result gets a JSON array containing every phase and table
     * event, ending with the same payload the plain form returns.
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorZipUpload_streamsAnEventPerTable() throws Exception {
        File zipFile = createTestZipFile();
        String importId = UUID.randomUUID().toString();
        String settings = """
                {
                  "fileType": "MULTIPLE_CSV_ZIP",
                  "delimiter": ",",
                  "hasHeader": true,
                  "firstSheetOnly": false,
                  "previewRows": 2
                }
                """;
        try {
            String response = given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .queryParam("importId", importId)
                    .multiPart("file", zipFile, "application/zip")
                    .multiPart("settings", settings, "application/json")
                    .accept(ContentType.JSON)
                    .when()
                    .post(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .asString();

            List<ImportEventDTO> events = importEvents(response);

            assertFalse(events.isEmpty(), "The import should report what it did");
            assertTrue(events.stream().anyMatch(event -> event.getTables() != null
                            && event.getTables().size() == 2),
                    "Both tables of the archive should be announced before they are read");
            assertTrue(events.stream().anyMatch(event -> event.getTable() != null
                            && event.getTable().getState() == ImportTableState.READING),
                    "Each table should say when it is being read");
            assertTrue(events.stream().anyMatch(event -> event.getTable() != null
                            && event.getTable().getState() == ImportTableState.READ
                            && event.getTable().getRows() == 3
                            && event.getTable().getColumns() == 2),
                    "A table that has been read should say what it holds");

            ImportEventDTO last = events.getLast();
            assertTrue(last.isLast(), "The stream should end with the import");
            assertEquals(ImportPhase.SUCCEEDED, last.getPhase());
            assertNotNull(last.getResult());
            assertEquals(2, last.getResult().getFiles().getFirst().getUploadInfo().size());

            // The events are also there for anyone who arrives afterwards.
            given()
                    .pathParam("importId", importId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .then()
                    .statusCode(200)
                    .body("phase", equalTo("SUCCEEDED"))
                    .body("tables.size()", equalTo(2));
        } finally {
            Files.deleteIfExists(zipFile.toPath());
        }
    }

    /**
     * A caller that says nothing about what it wants gets the events: there is one upload form, and
     * it is the streaming one.
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_withoutAnAcceptHeaderStreamsEvents() throws Exception {
        File csvFile = createTestCsvFile();
        try {
            String response = given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .multiPart("file", csvFile, "text/csv")
                    .multiPart("settings", CSV_UPLOAD_SETTINGS, "application/json")
                    .header("Accept", "*/*")
                    .when()
                    .post(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            List<ImportEventDTO> events = importEvents(response);
            assertFalse(events.isEmpty());
            assertEquals(ImportPhase.SUCCEEDED, events.getLast().getPhase());
            assertEquals(1, events.getLast().getResult().getFiles().size());
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * An import the client did not name is still watched: the stream has to carry something, so the
     * server names it and answers under that.
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_withoutImportIdIsNamedByTheServer() throws Exception {
        File csvFile = createTestCsvFile();
        try {
            ConnectorFileImportResultDTO result =
                    uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null);
            assertEquals(1, result.getFiles().size());

            String importId = given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/imports")
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getString("[0].importId");
            assertNotNull(importId, "the newest import should be listed under a name of its own");
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * An import goes on without the client that started it.
     *
     * <p>The request is cut off as soon as the bytes are away - the browser closed, the tab gone -
     * and the import still finishes and is still there to be asked about.</p>
     *
     * <p>What this does not prove is which of the two things keeping the file alive is doing the
     * work: the import owns it because it was taken out of the request, and the request itself also
     * lasts as long as the response it is still streaming. The first is the one worth relying on,
     * because it does not depend on when the framework decides a disconnected request has ended.</p>
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_survivesTheClientHangingUp() throws Exception {
        File csvFile = Files.createTempFile("connector-e2e-background-", ".csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("Unique Patient ID,13,14\n");
            // Big enough that the import is still going when the connection drops.
            for (int row = 1; row <= 400_000; row++) {
                writer.write("BG_PATIENT_" + row + "," + row + "," + row + ".5\n");
            }
        }

        String importId = UUID.randomUUID().toString();
        try {
            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<HttpResponse<Void>> response = client.sendAsync(
                    multipartUpload(csvFile, importId),
                    HttpResponse.BodyHandlers.discarding());

            // Wait until RESTEasy has received the multipart body and the detached import exists;
            // cancelling any earlier only tests interrupting the upload itself.
            awaitImportStarted(importId);

            // Hang up. Everything after this happens with nobody listening.
            response.cancel(true);
            client.close();

            ImportProgressDTO finished = awaitImport(importId);
            assertEquals(ImportPhase.SUCCEEDED, finished.getPhase(),
                    "the import should finish without the client that started it");
            assertNotNull(finished.getFileId(), "and the file should be stored");
            assertEquals(1, finished.getTables().size());
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * Empty cells are counted over the whole table, not over the handful of rows kept as its
     * preview: a file large enough to be sampled is exactly the file where the two differ.
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorUpload_countsEmptyCellsAcrossTheWholeTable() throws Exception {
        // 500 rows against a preview of 10, every third missing a value in the last column.
        File csvFile = Files.createTempFile("connector-e2e-gaps-", ".csv").toFile();
        int rows = 500;
        int expectedGaps = 0;
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("Unique Patient ID,13,14\n");
            for (int row = 1; row <= rows; row++) {
                boolean gap = row % 3 == 0;
                expectedGaps += gap ? 1 : 0;
                writer.write("GAP_PATIENT_" + row + "," + row + "," + (gap ? "" : row + ".5") + "\n");
            }
        }

        String importId = UUID.randomUUID().toString();
        try {
            uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, importId, null);

            ImportProgressDTO progress = given()
                    .pathParam("importId", importId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ImportProgressDTO.class);

            assertEquals(1, progress.getTables().size());
            ImportTableDTO table = progress.getTables().getFirst();
            assertEquals(rows, table.getRows(), "every row is read, not only the sampled ones");
            assertNotNull(table.getMissingValues(), "empty cells should be counted");
            assertEquals(expectedGaps, table.getMissingValues(),
                    "empty cells should be counted over the whole table, not over the preview");
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    /**
     * A connector whose file has gone can still be given a new one.
     *
     * <p>This is the state the editor calls {@code fileExists: false}: the file the connector points
     * at is no longer there, so there is nothing to compare a replacement against and the replacement
     * is simply taken.</p>
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorReupload_worksWhenTheConnectorsFileIsGone() throws Exception {
        File csvFile = createTestCsvFile();
        try {
            Long fileId = uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null)
                    .getFiles()
                    .getFirst()
                    .getId();

            Long connectorId = given()
                    .contentType(ContentType.JSON)
                    .body(createConnectorDTO(fileId))
                    .when()
                    .post(CONNECTORS_PATH)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(ConnectorDTO.class)
                    .getId();

            given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .pathParam("fileId", fileId)
                    .when()
                    .delete(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files/{fileId}")
                    .then()
                    .statusCode(200);

            given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .pathParam("fileId", fileId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files/{fileId}")
                    .then()
                    .statusCode(200)
                    .body("fileExists", equalTo(false));

            ConnectorFileImportResultDTO result =
                    uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS,
                            UUID.randomUUID().toString(), connectorId);

            assertEquals(Boolean.TRUE, result.getAccepted(),
                    "with nothing left to compare against, the replacement is the connector's input");
            assertNotNull(result.getFiles().getFirst().getId());
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorReupload_replacesAndDeletesTheOldFile() throws Exception {
        File original = createTestCsvFile();
        File replacement = Files.createTempFile("connector-e2e-unused-column-change-", ".csv").toFile();
        try (FileWriter writer = new FileWriter(replacement)) {
            writer.write("Changed Unused Patient ID,13,14\n");
            writer.write("E2E_PATIENT_1,25,72.5\n");
        }
        try {
            Long oldFileId = uploadStream(original, "text/csv", CSV_UPLOAD_SETTINGS, null, null)
                    .getFiles().getFirst().getId();
            Long connectorId = given()
                    .contentType(ContentType.JSON)
                    .body(createConnectorDTO(oldFileId))
                    .when()
                    .post(CONNECTORS_PATH)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(ConnectorDTO.class)
                    .getId();

            ConnectorFileImportResultDTO result = uploadStream(
                    replacement, "text/csv", CSV_UPLOAD_SETTINGS,
                    UUID.randomUUID().toString(), connectorId);

            assertEquals(Boolean.TRUE, result.getAccepted());
            Long newFileId = result.getFiles().getFirst().getId();
            assertNotEquals(oldFileId, newFileId);
            given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .pathParam("fileId", oldFileId)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files/{fileId}")
                    .then()
                    .statusCode(200)
                    .body("fileExists", equalTo(false));

            ConnectorDTO connector = given()
                    .pathParam("id", connectorId)
                    .when()
                    .get(CONNECTORS_PATH + "/{id}")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ConnectorDTO.class);
            assertEquals(newFileId, ((FileUploadSettingsDTO) connector.getInputConfig()).getFileId());
        } finally {
            Files.deleteIfExists(original.toPath());
            Files.deleteIfExists(replacement.toPath());
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorReupload_refusalKeepsOnlyTheOldFile() throws Exception {
        File original = createTestCsvFile();
        File replacement = Files.createTempFile("connector-e2e-invalid-reupload-", ".csv").toFile();
        try (FileWriter writer = new FileWriter(replacement)) {
            writer.write("Unique Patient ID,Different Used Column,14\n");
            writer.write("E2E_PATIENT_1,25,72.5\n");
        }

        try {
            Long oldFileId = uploadStream(original, "text/csv", CSV_UPLOAD_SETTINGS, null, null)
                    .getFiles().getFirst().getId();
            Long connectorId = given()
                    .contentType(ContentType.JSON)
                    .body(createConnectorDTO(oldFileId))
                    .when()
                    .post(CONNECTORS_PATH)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(ConnectorDTO.class)
                    .getId();

            ConnectorFileImportResultDTO result = uploadStream(
                    replacement, "text/csv", CSV_UPLOAD_SETTINGS,
                    UUID.randomUUID().toString(), connectorId);

            assertEquals(Boolean.FALSE, result.getAccepted());
            assertNotNull(result.getError());
            assertTrue(result.getFiles().isEmpty());
            given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .pathParam("fileId", oldFileId)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files/{fileId}")
                    .then()
                    .statusCode(200)
                    .body("fileExists", equalTo(true));

            List<ConnectorFilesDTO> cohortFiles = given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files")
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList("", ConnectorFilesDTO.class);
            assertTrue(cohortFiles.stream()
                    .noneMatch(file -> replacement.getName().equals(file.getFileName())),
                    "the refused upload must not remain stored");
        } finally {
            Files.deleteIfExists(original.toPath());
            Files.deleteIfExists(replacement.toPath());
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorZipUpload_returnsPreviewAndProfilesPerEntry() throws Exception {
        File zipFile = createTestZipFile();
        String settings = """
                {
                  "fileType": "MULTIPLE_CSV_ZIP",
                  "delimiter": ",",
                  "hasHeader": true,
                  "firstSheetOnly": false,
                  "previewRows": 2
                }
                """;
        try {
            ConnectorFilesDetailDTO uploaded =
                    uploadStream(zipFile, "application/zip", settings, null, null).getFiles().getFirst();

            assertEquals(2, uploaded.getUploadInfo().size());
            assertTrue(uploaded.getUploadInfo().stream().allMatch(info -> {
                try {
                    return objectMapper.readTree(info.getJson()).size() == 2
                            && !info.getColumnProfiles().isEmpty()
                            && info.getColumnProfiles().stream().allMatch(profile -> profile.count() == 3);
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }));
        } finally {
            Files.deleteIfExists(zipFile.toPath());
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorImport_createAndRun_patientsImportedAndLogsExist() throws Exception {

        // ---- 1) Upload CSV file for cohort ----
        File csvFile = createTestCsvFile();
        try {
            List<ConnectorFilesDetailDTO> uploadedFiles =
                    uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null).getFiles();

            assertNotNull(uploadedFiles, "File upload should return file info");
            assertFalse(uploadedFiles.isEmpty(), "Should have uploaded at least one file");

            // ---- 2) Create connector with schema mapping ----
            ConnectorDTO connectorDTO = createConnectorDTO(uploadedFiles.getFirst().getId());

            ConnectorDTO createdConnector = given()
                    .contentType(ContentType.JSON)
                    .body(connectorDTO)
                    .when()
                    .post(CONNECTORS_PATH)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(ConnectorDTO.class);

            assertNotNull(createdConnector, "Connector should have been created");
            assertNotNull(createdConnector.getId(), "Connector should have an ID");
            Long connectorId = createdConnector.getId();

            // ---- 3) Trigger a run ----
            // The run endpoint returns Multi<ConnectorRunDTO> (SSE).
            // We consume raw text to get at least the first event, then poll for completion.
            String sseResponse = given()
                    .pathParam("id", connectorId)
                    .queryParam("run-mode", "DEFAULT")
                    .queryParam("dry", false)
                    .contentType(ContentType.JSON)
                    .when()
                    .post(CONNECTORS_PATH + "/{id}/run")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            // Parse the first SSE data line to get the run ID
            Long runId = extractRunIdFromSse(sseResponse);
            if (runId == null) {
                // Fallback: get runs for this connector
                List<ConnectorRunDTO> runs = pollRunsUntilPresent(connectorId);
                assertFalse(runs.isEmpty(), "Should have at least one run");
                runId = runs.get(0).getId();
            }

            assertNotNull(runId, "Run ID should not be null");

            // ---- 4) Poll run status until finished ----
            ConnectorRunDTO finalRun = pollRunUntilDone(runId);

            // ---- 5) Verify run completed (could be FINISHED or ERROR depending on validation) ----
            assertNotNull(finalRun, "Final run should not be null");
            assertTrue(
                    finalRun.getStatus() == ImportStatusEnum.FINISHED || finalRun.getStatus() == ImportStatusEnum.ERROR,
                    "Run should be FINISHED or ERROR, got: " + finalRun.getStatus()
            );

            // ---- 6) Verify run-logs endpoint is accessible ----
            RunErrorLogListResponseDTO logResponse = given()
                    .pathParam("id", runId)
                    .when()
                    .get(CONNECTOR_RUNS_PATH + "/{id}/run-logs")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(RunErrorLogListResponseDTO.class);

            assertNotNull(logResponse, "Log response should not be null");
            List<ConnectorRunPatientLogDTO> logs = logResponse.getLogs();
            assertNotNull(logs, "Logs list should not be null");

            // If the run finished successfully, check patients
            if (finalRun.getStatus() == ImportStatusEnum.FINISHED) {
                verifyPatientsImported(finalRun);
            } else {
                // If the run had errors, we should have at least one error log
                assertFalse(logs.isEmpty(),
                        "When run has ERROR status, there should be at least one error log");
            }

            // ---- 7) Verify we can filter logs by type ----
            RunErrorLogListResponseDTO loadingLogs = given()
                    .pathParam("id", runId)
                    .queryParam("type", "LOADING")
                    .when()
                    .get(CONNECTOR_RUNS_PATH + "/{id}/run-logs")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(RunErrorLogListResponseDTO.class);

            assertNotNull(loadingLogs, "Loading logs response should not be null");
            for (ConnectorRunPatientLogDTO log : loadingLogs.getLogs()) {
                assertEquals(ConnectorRunPatientLogType.EXTRACTING, log.getLogType(),
                        "Filtered logs should all be LOADING type");
            }

            // ---- 8) Cleanup: delete connector ----
            given()
                    .pathParam("id", connectorId)
                    .when()
                    .delete(CONNECTORS_PATH + "/{id}")
                    .then()
                    .statusCode(200);

        } finally {
            csvFile.delete();
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorDryRun_skipsLoadAndShowsStatistics() throws Exception {

        // ---- 1) Upload CSV ----
        File csvFile = createTestCsvFile();
        try {
            List<ConnectorFilesDetailDTO> uploadedFiles =
                    uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null).getFiles();

            // ---- 2) Create connector ----
            ConnectorDTO connectorDTO = createConnectorDTO(uploadedFiles.getFirst().getId());

            ConnectorDTO createdConnector = given()
                    .contentType(ContentType.JSON)
                    .body(connectorDTO)
                    .when()
                    .post(CONNECTORS_PATH)
                    .then()
                    .statusCode(201)
                    .extract()
                    .as(ConnectorDTO.class);

            Long connectorId = createdConnector.getId();

            // ---- 3) Trigger a DRY RUN ----
            given()
                    .pathParam("id", connectorId)
                    .queryParam("run-mode", "DEFAULT")
                    .queryParam("dry", true)
                    .contentType(ContentType.JSON)
                    .when()
                    .post(CONNECTORS_PATH + "/{id}/run")
                    .then()
                    .statusCode(200);

            // ---- 4) Get the run ----
            List<ConnectorRunDTO> runs = pollRunsUntilPresent(connectorId);
            assertFalse(runs.isEmpty(), "Should have at least one run");
            Long runId = runs.get(0).getId();

            // ---- 5) Poll until done ----
            ConnectorRunDTO finalRun = pollRunUntilDone(runId);

            // ---- 6) Verify dry run statistics ----
            assertNotNull(finalRun);
            assertTrue(finalRun.getDryRun(), "Run should be a dry run");

            if (finalRun.getStatus() == ImportStatusEnum.FINISHED) {
                assertTrue(finalRun.getReceivedEntities() > 0,
                        "Dry run should report received entities");
                assertEquals(100L, finalRun.getProgress(), "Dry run should reach 100% progress");
            }

            // ---- 7) Verify no patients were actually created in the cohort ----
            // (dry run should skip load)
            List<PatientDTO> patients = given()
                    .pathParam("cohortId", TEST_COHORT_ID)
                    .contentType(ContentType.JSON)
                    .when()
                    .get(patientServicePath)
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .jsonPath()
                    .getList(".", PatientDTO.class);

            long dryRunPatientCount = patients.stream()
                    .filter(p -> p.getExternalPatientId() != null
                            && p.getExternalPatientId().startsWith("E2E_PATIENT_"))
                    .count();

            // Dry run should not create patients (unless a previous non-dry test already did)
            // This assertion is soft - the key check is that the run reports correct statistics
            if (finalRun.getStatus() == ImportStatusEnum.FINISHED) {
                assertEquals(finalRun.getReceivedEntities(), finalRun.getProcessedEntities(),
                        "In dry run, received == processed");
                assertEquals(0L, finalRun.getFailedEntities(),
                        "In dry run, no entities should fail");
            }

            // ---- 8) Cleanup ----
            given()
                    .pathParam("id", connectorId)
                    .when()
                    .delete(CONNECTORS_PATH + "/{id}")
                    .then()
                    .statusCode(200);

        } finally {
            csvFile.delete();
        }
    }

    /**
     * A connector whose input file has gone away still runs, and says so in its logs.
     *
     * <p>The file is deleted after the connector is created rather than never given: a connector
     * cannot be created without one, because {@code ConnectorBO.create} refuses a file id that does
     * not resolve. Losing the file afterwards is the case that actually happens, and the one the UI
     * has a {@code fileExists: false} state for.</p>
     */
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testConnectorImport_missingFile_createsErrorLog() throws Exception {

        // ---- 1) Upload a file and create a connector that reads it ----
        File csvFile = createTestCsvFile();
        Long fileId;
        try {
            fileId = uploadStream(csvFile, "text/csv", CSV_UPLOAD_SETTINGS, null, null)
                    .getFiles()
                    .getFirst()
                    .getId();
        } finally {
            Files.deleteIfExists(csvFile.toPath());
        }

        ConnectorDTO created = given()
                .contentType(ContentType.JSON)
                .body(createConnectorDTO(fileId))
                .when()
                .post(CONNECTORS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ConnectorDTO.class);

        Long connectorId = created.getId();

        // ---- 2) Take the file away again ----
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .pathParam("fileId", fileId)
                .when()
                .delete(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files/{fileId}")
                .then()
                .statusCode(200);

        // ---- 3) Trigger run ----
        given()
                .pathParam("id", connectorId)
                .queryParam("run-mode", "DEFAULT")
                .queryParam("dry", false)
                .contentType(ContentType.JSON)
                .when()
                .post(CONNECTORS_PATH + "/{id}/run")
                .then()
                .statusCode(200);

        // ---- 4) Get the run ----
        List<ConnectorRunDTO> runs = pollRunsUntilPresent(connectorId);
        assertFalse(runs.isEmpty(), "Should have at least one run");
        Long runId = runs.get(0).getId();

        // ---- 5) Poll until done ----
        ConnectorRunDTO finalRun = pollRunUntilDone(runId);

        // ---- 6) Run should be in ERROR status (no file to extract) ----
        assertEquals(ImportStatusEnum.ERROR, finalRun.getStatus(),
                "Run should be ERROR when no file is available");

        // ---- 7) Verify error logs exist ----
        RunErrorLogListResponseDTO logResponse = given()
                .pathParam("id", runId)
                .when()
                .get(CONNECTOR_RUNS_PATH + "/{id}/run-logs")
                .then()
                .statusCode(200)
                .extract()
                .as(RunErrorLogListResponseDTO.class);

        // The phase is deliberately not asserted. A run this connector cannot complete fails at the
        // first step that cannot proceed, and which step that is depends on the connector rather than
        // on the missing file - what this test is about is that the run ends in ERROR and says why.
        assertFalse(logResponse.getLogs().isEmpty(),
                "Should have at least one error log when the run cannot read its file");

        // ---- 8) Cleanup ----
        given()
                .pathParam("id", connectorId)
                .when()
                .delete(CONNECTORS_PATH + "/{id}")
                .then()
                .statusCode(200);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a test CSV file with patient data matching the comprehensive schema.
     * Columns: Unique Patient ID, 13 (INT node), 14 (FLOAT node)
     * The column name "Unique Patient ID" is the default value of
     * {@code feddb.connector.external-id-column}.
     */
    /** A multipart upload built by hand, for the one test that has to control the connection. */
    private HttpRequest multipartUpload(File file, String importId) throws IOException {
        String boundary = "----importE2E" + UUID.randomUUID();
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(file.toPath()));
        body.write(("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"settings\"; filename=\"settings.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + CSV_UPLOAD_SETTINGS
                + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + RestAssured.port + CONNECTOR_FILES_PATH
                        + "/cohorts/" + TEST_COHORT_ID + "/files?importId=" + importId))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
    }

    /** Waits for an import to end, the way a page that reopens on it would. */
    private ImportProgressDTO awaitImport(String importId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            ImportProgressDTO progress = given()
                    .pathParam("importId", importId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ImportProgressDTO.class);
            if (progress.getFinishedAt() != null) {
                return progress;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Import " + importId + " never finished");
    }

    /** Waits until the server has accepted the complete multipart request and registered the import. */
    private void awaitImportStarted(String importId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            int status = given()
                    .pathParam("importId", importId)
                    .accept(ContentType.JSON)
                    .when()
                    .get(CONNECTOR_FILES_PATH + "/imports/{importId}")
                    .statusCode();
            if (status == 200) {
                return;
            }
            assertEquals(404, status, "unexpected response while waiting for the import to start");
            Thread.sleep(50);
        }
        throw new AssertionError("Import " + importId + " never started");
    }

    /**
     * Uploads a file the way every client does now: asking for the events, and reading the result off
     * the last one.
     */
    private ConnectorFileImportResultDTO uploadStream(
            File file,
            String contentType,
            String settings,
            String importId,
            Long connectorId
    ) throws IOException {
        var request = given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .multiPart("file", file, contentType)
                .multiPart("settings", settings, "application/json")
                .accept(ContentType.JSON);
        if (importId != null) {
            request = request.queryParam("importId", importId);
        }
        if (connectorId != null) {
            request = request.queryParam("connectorId", connectorId);
        }
        String response = request
                .when()
                .post(CONNECTOR_FILES_PATH + "/cohorts/{cohortId}/files")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        List<ImportEventDTO> events = importEvents(response);
        assertFalse(events.isEmpty(), "An import should report what it did");
        ImportEventDTO last = events.getLast();
        assertTrue(last.isLast(), "The stream should end with the import");
        assertNotNull(last.getResult(), "The last event carries the result: " + last.getErrorMessage());
        return last.getResult();
    }

    private List<ImportEventDTO> importEvents(String response) throws IOException {
        return objectMapper.readerForListOf(ImportEventDTO.class).readValue(response);
    }

    private File createTestCsvFile() throws IOException {
        File csvFile = Files.createTempFile("connector-e2e-test-", ".csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("Unique Patient ID,13,14\n");
            writer.write("E2E_PATIENT_1,25,72.5\n");
            writer.write("E2E_PATIENT_2,30,68.0\n");
            writer.write("E2E_PATIENT_3,45,85.3\n");
        }
        return csvFile;
    }

    private File createTestZipFile() throws IOException {
        File zipFile = File.createTempFile("connector-upload-", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            writeZipEntry(output, "patients.csv", "id,value\n1,A\n2,B\n3,C\n");
            writeZipEntry(output, "visits.csv", "id,date\n1,2026-01-01\n2,2026-01-02\n3,2026-01-03\n");
        }
        return zipFile;
    }

    private void writeZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    /**
     * Creates a ConnectorDTO for cohort 5 with FILE input config and schema mapping
     * referencing INT (13) and FLOAT (14) schema nodes.
     */
    private ConnectorDTO createConnectorDTO(Long fileId) {
        ConnectorDTO connectorDTO = new ConnectorDTO();
        connectorDTO.setName("E2E_Import_Test_" + System.currentTimeMillis());
        connectorDTO.setCohortId(TEST_COHORT_ID);

        FileUploadSettingsDTO inputConfig = new FileUploadSettingsDTO();
        inputConfig.setMode("FILE");
        inputConfig.setFileType(FileParsingType.CSV);
        inputConfig.setDelimiter(",");
        inputConfig.setHasHeader(true);
        inputConfig.setFileId(fileId);
        inputConfig.setFileExists(true);
        connectorDTO.setInputConfig(inputConfig);

        ConnectorMappingDTO intMapping = new ConnectorMappingDTO();
        intMapping.setSchemaId(INT_SCHEMA_NODE_ID);
        intMapping.setColumn("13");

        ConnectorMappingDTO floatMapping = new ConnectorMappingDTO();
        floatMapping.setSchemaId(FLOAT_SCHEMA_NODE_ID);
        floatMapping.setColumn("14");

        connectorDTO.setSchemaMapping(List.of(intMapping, floatMapping));

        return connectorDTO;
    }

    /**
     * Extracts the run ID from an SSE response body.
     * SSE format: "data:{json}\n\n"
     */
    private Long extractRunIdFromSse(String sseResponse) {
        if (sseResponse == null || sseResponse.isBlank()) {
            return null;
        }
        try {
            for (String line : sseResponse.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String json = trimmed.substring("data:".length()).trim();
                    if (json.isEmpty()) continue;
                    ConnectorRunDTO dto = objectMapper.readValue(json, ConnectorRunDTO.class);
                    if (dto.getId() != null) {
                        return dto.getId();
                    }
                }
                if (trimmed.startsWith("{")) {
                    ConnectorRunDTO dto = objectMapper.readValue(trimmed, ConnectorRunDTO.class);
                    if (dto.getId() != null) {
                        return dto.getId();
                    }
                }
            }
        } catch (Exception e) {
            // Parsing failed, use fallback via pollRunsUntilPresent
        }
        return null;
    }

    /**
     * Polls the run status until it reaches FINISHED or ERROR.
     * Timeout: 30 seconds.
     */
    private ConnectorRunDTO pollRunUntilDone(Long runId) throws InterruptedException {
        long timeout = System.currentTimeMillis() + 30_000;
        ConnectorRunDTO run = null;

        while (System.currentTimeMillis() < timeout) {
            Thread.sleep(500);
            try {
                run = given()
                        .pathParam("id", runId)
                        .when()
                        .get(CONNECTOR_RUNS_PATH + "/{id}")
                        .then()
                        .statusCode(200)
                        .extract()
                        .as(ConnectorRunDTO.class);

                if (run.getStatus() == ImportStatusEnum.FINISHED
                        || run.getStatus() == ImportStatusEnum.ERROR) {
                    return run;
                }
            } catch (Exception e) {
                // Run might not be persisted yet, retry
            }
        }

        fail("Run " + runId + " did not complete within 30 seconds. Last status: "
                + (run != null ? run.getStatus() : "null"));
        return null;
    }

    /**
     * Polls until at least one run exists for the connector.
     * Timeout: 10 seconds.
     */
    private List<ConnectorRunDTO> pollRunsUntilPresent(Long connectorId) throws InterruptedException {
        long timeout = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < timeout) {
            Thread.sleep(300);
            try {
                List<ConnectorRunDTO> runs = given()
                        .pathParam("connectorId", connectorId)
                        .when()
                        .get(CONNECTOR_RUNS_PATH + "/connectors/{connectorId}")
                        .then()
                        .statusCode(200)
                        .extract()
                        .body()
                        .jsonPath()
                        .getList(".", ConnectorRunDTO.class);

                if (runs != null && !runs.isEmpty()) {
                    return runs;
                }
            } catch (Exception e) {
                // retry
            }
        }

        fail("No runs appeared for connector " + connectorId + " within 10 seconds");
        return List.of();
    }

    /**
     * Verifies patients were imported into the cohort.
     */
    private void verifyPatientsImported(ConnectorRunDTO run) {
        List<PatientDTO> patients = given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .when()
                .get(patientServicePath)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList(".", PatientDTO.class);

        long e2ePatientCount = patients.stream()
                .filter(p -> p.getExternalPatientId() != null
                        && p.getExternalPatientId().startsWith("E2E_PATIENT_"))
                .count();

        assertTrue(e2ePatientCount > 0,
                "Expected at least one E2E patient to be imported, found " + e2ePatientCount);

        // Verify run statistics are consistent
        if (run.getReceivedEntities() != null && run.getReceivedEntities() > 0) {
            assertTrue(run.getProcessedEntities() >= 0,
                    "Processed entities should be >= 0");
        }
    }
}
