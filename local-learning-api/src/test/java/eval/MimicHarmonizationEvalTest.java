package eval;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluation test for harmonizing a <em>structurally different</em> source onto
 * the shared federated diabetes schema (paper requirements RE1 — built-in
 * harmonization, RE2 — shared schema).
 * <p>
 * {@link HarmonizationEvalTest} covers heterogeneity <em>within</em> one dataset:
 * five sites export the same US-130 table with different vocabularies, units and
 * layouts. This test covers the harder case — a different resource altogether.
 * MIMIC-IV v2.2 (demo extract) is a relational clinical database in which almost
 * nothing the schema asks for exists as a column:
 * <ul>
 *   <li>demographics, encounters, diagnoses and measurements live in
 *       <em>separate tables</em> that only share {@code subject_id};</li>
 *   <li>the length of stay exists only as two timestamps;</li>
 *   <li>the laboratory categories exist only as thousands of long-format
 *       {@code labevents} rows identified by item id;</li>
 *   <li>the volume-of-care counts exist only as the number of rows an encounter
 *       has in the event tables;</li>
 *   <li>the 23 medication variables exist only as free-text prescription
 *       orders;</li>
 *   <li>the readmission outcome and the prior-year contact counts exist only
 *       implicitly, in the spacing of a patient's admissions.</li>
 * </ul>
 * Onboarding still happens through the standard FL-Net workflow and nothing but
 * configuration: the declarative connector
 * {@code /connectors/mimic-iv-demo-2.2.json} (sheet merge + transformer chain +
 * schema mapping) plus the shared global schema (mocked here from
 * {@code /connectors/global_schema_dummy.json}).
 * <p>
 * The test asserts that the import runs, that patients are loaded, that every
 * shared-schema field the evaluation queries is not merely <em>declared</em> but
 * actually <em>populated</em> with values from MIMIC-IV, and that harmonization
 * is lossless — no value is rejected by the schema validation. Two reports are
 * printed and written under {@code target/harmonization-eval/}: a run summary and
 * a per-field coverage table.
 */
@QuarkusTest
@TestProfile(MimicHarmonizationEvalTest.MimicProfile.class)
public class MimicHarmonizationEvalTest {

    /**
     * The {@code mimic} auto-subscribe group is configured for {@code %dev} only —
     * activating it in {@code %test} would make every test that subscribes to all
     * groups import the whole demo database — so this profile supplies it. OIDC
     * and the startup global websocket are disabled so the evaluation boots
     * self-contained and stays CI-reproducible.
     */
    public static class MimicProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("quarkus.oidc.enabled", "false"),
                    Map.entry("flnet.global.socket.enabled", "false"),
                    Map.entry("flnet.auto-subscribe.mimic.global-schema-id",
                            "bef52fc9-faed-44b4-b3ac-e786b4731bcf"),
                    Map.entry("flnet.auto-subscribe.mimic.cohort-name", COHORT_NAME),
                    Map.entry("flnet.auto-subscribe.mimic.etl-data-path", DATA_PATH),
                    Map.entry("flnet.auto-subscribe.mimic.connector-path", CONNECTOR_PATH),
                    Map.entry("flnet.auto-subscribe.mimic.add-for-user", "test"),
                    Map.entry("flnet.auto-subscribe.mimic.query-sample-threshold", "25"),
                    Map.entry("flnet.auto-subscribe.mimic.enabled-file-watching", "false"));
        }
    }

    static final String GROUP_KEY = "mimic";
    static final String COHORT_NAME = "MIMIC IV Demo Schema";
    static final String DATA_PATH = "/connectors/mimic-iv-demo-2.2.zip";
    static final String CONNECTOR_PATH = "/connectors/mimic-iv-demo-2.2.json";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";

    /** The demo extract ships 100 patients across 27 tables. */
    private static final long EXPECTED_PATIENTS = 100L;

    /**
     * The shared-schema fields the federated evaluation actually queries. Every
     * one of them has to be reconstructed from MIMIC-IV rather than read from a
     * column, so requiring them to carry values is the real harmonization check.
     */
    private static final List<String> REQUIRED_FIELDS = List.of(
            "Race", "Gender", "Age", "A1C Result", "Max Glu Serum",
            "Diabetes Med", "Change", "Readmitted", "Metformin", "Insulin",
            "Time in Hospital", "Num Lab Procedures");

    /** Which MIMIC-IV tables each schema field is reconstructed from (for the report). */
    private static final Map<String, String> FIELD_SOURCES = fieldSources();

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Inject
    ConnectorRunPatientLogBO patientLogBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    PatientAO patientAO;

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    @Inject
    SchemaNodeAO schemaNodeAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void installGlobalSchemaMock() throws Exception {
        SchemaStructureDTO dummySchema;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SCHEMA_DUMMY_PATH.substring(1))) {
            assertNotNull(in, "Test dummy schema " + SCHEMA_DUMMY_PATH + " must be on the classpath");
            dummySchema = objectMapper.readValue(in, SchemaStructureDTO.class);
        }

        GlobalSchemaSubscriptionService mock = new GlobalSchemaSubscriptionService() {
            @Override
            public Uni<SchemaStructureDTO> subscribe(UUID id) {
                return Uni.createFrom().item(dummySchema);
            }

            @Override
            public Uni<Response> unsubscribe(UUID id) {
                return Uni.createFrom().item(Response.ok().build());
            }
        };

        QuarkusMock.installMockForType(mock, GlobalSchemaSubscriptionService.class, RestClient.LITERAL);
    }

    @Test
    void harmonizeMimicIvOntoTheSharedSchema() throws Exception {
        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = config.autoSubscribe().groups().get(GROUP_KEY);
        assertNotNull(groupConfig, "Auto-subscribe group '" + GROUP_KEY + "' must be configured for this profile");
        String keycloakId = groupConfig.addForUser().orElseGet(() -> config.user().systemUserName());

        // --- Onboarding: cohort + declarative connector, then the import run ---
        ConnectorDTO connector = autoSubscriberBO.prepareSubscription(GROUP_KEY, groupConfig, keycloakId);
        assertNotNull(connector, "Connector for the MIMIC-IV group should be created");
        assertEquals(51, connector.getSchemaMapping().size(),
                "The MIMIC-IV connector should map the external patient id plus 50 shared-schema fields");

        ConnectorRunDTO run = connectorRunBO.startRun(connector.getId(), true, false, keycloakId);
        assertNotNull(run, "Connector run should start");

        Long cohortId = QuarkusTransaction.requiringNew()
                .call(() -> cohortAO.findByName(COHORT_NAME).map(CohortEntity::getId))
                .orElseThrow(() -> new AssertionError("Cohort '" + COHORT_NAME + "' was not created"));

        long patients = awaitImport(run.getId(), cohortId, 3_600_000L);

        // --- RE1/RE2: a complete, schema-valid local representation, by config alone ---
        assertEquals(EXPECTED_PATIENTS, patients,
                "All patients of the MIMIC-IV demo extract should be imported");

        Map<String, Long> populated = populatedFields(cohortId);
        Set<String> declared = declaredFields(cohortId);
        for (String field : REQUIRED_FIELDS) {
            assertTrue(declared.contains(field),
                    "Shared-schema field '" + field + "' should exist after subscribing to the schema");
            assertTrue(populated.getOrDefault(field, 0L) > 0,
                    "Shared-schema field '" + field + "' should carry harmonised MIMIC-IV values, "
                            + "but no data entry was loaded for it");
        }

        // RE1: harmonization is lossless — no value is rejected or coerced against
        // the shared schema once the connector has run.
        List<ConnectorRunPatientLogDTO> logs = QuarkusTransaction.requiringNew()
                .call(() -> patientLogBO.getByRunId(run.getId(), null, null));
        List<String> rejections = logs.stream()
                .map(ConnectorRunPatientLogDTO::getMessage)
                .filter(this::isValueRejection)
                .toList();
        assertEquals(List.of(), rejections.size() > 5 ? rejections.subList(0, 5) : rejections,
                "MIMIC-IV should harmonise with no value rejections/coercions ("
                        + rejections.size() + " in total)");

        writeReports(cohortId, patients, populated, declared, rejections.size());
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    private void writeReports(Long cohortId,
                              long patients,
                              Map<String, Long> populated,
                              Set<String> declared,
                              long rejections) throws Exception {
        long patientsWithData = QuarkusTransaction.requiringNew().call(() ->
                patientDataEntryAO.getEntityManager().createQuery(
                                "select count(distinct e.patient.id) from PatientDataEntryEntity e "
                                        + "where e.patient.cohort.id = :cohortId", Long.class)
                        .setParameter("cohortId", cohortId)
                        .getSingleResult());
        long dataEntries = populated.values().stream().mapToLong(Long::longValue).sum();
        long fieldsWithData = populated.values().stream().filter(count -> count > 0).count();

        StringBuilder summary = new StringBuilder(
                "Source,Source Tables,Source Rows,Patients Imported,Patients With Data,"
                        + "Schema Fields Declared,Schema Fields Populated,Data Entries,Value Rejections,Status\n");
        summary.append(String.format("MIMIC-IV v2.2 demo,%d,%d,%d,%d,%d,%d,%d,%d,%s%n",
                SOURCE_TABLES, SOURCE_ROWS, patients, patientsWithData,
                declared.size(), fieldsWithData, dataEntries, rejections,
                rejections == 0 ? "OK" : "REJECTIONS"));

        StringBuilder coverage = new StringBuilder("Schema Field,MIMIC-IV Source,Data Entries Loaded,Populated\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(FIELD_SOURCES).entrySet()) {
            long count = populated.getOrDefault(entry.getKey(), 0L);
            coverage.append(String.format("%s,%s,%d,%s%n",
                    entry.getKey(), entry.getValue(), count, count > 0 ? "Yes" : "No"));
        }

        System.out.println("\n[MIMIC-HARMONIZATION] Run summary (MIMIC-IV v2.2 -> shared diabetes schema)\n" + summary);
        System.out.println("[MIMIC-HARMONIZATION] Field coverage\n" + coverage);

        Path outDir = Path.of("target", "harmonization-eval");
        Files.createDirectories(outDir);
        Path summaryFile = outDir.resolve("mimic-harmonization-report.csv");
        Path coverageFile = outDir.resolve("mimic-field-coverage.csv");
        Files.writeString(summaryFile, summary.toString(), StandardCharsets.UTF_8);
        Files.writeString(coverageFile, coverage.toString(), StandardCharsets.UTF_8);
        System.out.println("[MIMIC-HARMONIZATION] Saved: " + summaryFile.toAbsolutePath());
        System.out.println("[MIMIC-HARMONIZATION] Saved: " + coverageFile.toAbsolutePath());
    }

    /** Tables merged by the connector, and the rows they contribute (the archive holds 24). */
    private static final int SOURCE_TABLES = 8;
    private static final long SOURCE_ROWS = 134_700L;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * True iff an import log records a <em>value</em> being rejected or coerced
     * during schema validation. Structural notices — most importantly the "atomic
     * attribute has multiple entries" message emitted when several source rows
     * feed the same visit — are not value problems and are excluded, so the count
     * reflects genuine harmonization defects only.
     */
    private boolean isValueRejection(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("is not allowed")
                || normalized.contains("cannot be normalized")
                || normalized.contains("is required")
                || normalized.contains("out of range")
                || normalized.contains("does not match required pattern");
    }

    /**
     * Waits for the asynchronous connector run. The run reaches a terminal status
     * through an SSE {@code sendUpdate} that can fail in a test environment, so a
     * settled patient count (unchanged across several reads) is accepted as well.
     */
    private long awaitImport(Long runId, Long cohortId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long previous = -1;
        int stableReads = 0;
        long current = 0;
        while (System.currentTimeMillis() < deadline) {
            current = QuarkusTransaction.requiringNew().call(() -> patientAO.countByCohortId(cohortId));
            ImportStatusEnum status = QuarkusTransaction.requiringNew()
                    .call(() -> connectorRunBO.getById(runId).getStatus());
            if (status == ImportStatusEnum.FINISHED || status == ImportStatusEnum.ERROR) {
                assertEquals(ImportStatusEnum.FINISHED, status, "The MIMIC-IV import run should not fail");
                return current;
            }
            // The ETL loads one patient group (~10k merged rows) at a time, so allow
            // a generous quiet period before treating the count as settled.
            if (current > 0 && current == previous) {
                if (++stableReads >= 15) {
                    return current;
                }
            } else {
                stableReads = 0;
            }
            previous = current;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return current;
            }
        }
        return current;
    }

    /** Shared-schema field names that resolved to an ontology + datatype for the cohort. */
    private Set<String> declaredFields(Long cohortId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Set<String> names = new HashSet<>();
            for (SchemaNodeEntity node : schemaNodeAO.getSchemaNodesForCohort(cohortId)) {
                if (node.getOntology() == null || node.getDataType() == null) {
                    continue; // group / structural nodes carry no ontology
                }
                names.add(node.getName());
            }
            return names;
        });
    }

    /** Number of loaded data entries per shared-schema field — the real coverage measure. */
    private Map<String, Long> populatedFields(Long cohortId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            List<Object[]> rows = patientDataEntryAO.getEntityManager().createQuery(
                            "select e.schemaNode.name, count(e) from PatientDataEntryEntity e "
                                    + "where e.patient.cohort.id = :cohortId group by e.schemaNode.name",
                            Object[].class)
                    .setParameter("cohortId", cohortId)
                    .getResultList();
            for (Object[] row : rows) {
                counts.put((String) row[0], (Long) row[1]);
            }
            return counts;
        });
    }

    private static Map<String, String> fieldSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Race", "admissions.race");
        sources.put("Gender", "patients.gender");
        sources.put("Age", "patients.anchor_age");
        sources.put("Weight", "omr (Weight (Lbs), latest)");
        sources.put("Encounter ID", "admissions.hadm_id");
        sources.put("Patient Number", "admissions.subject_id");
        sources.put("Admission Type", "admissions.admission_type");
        sources.put("Admission Source", "admissions.admission_location");
        sources.put("Discharge Disposition", "admissions.discharge_location");
        sources.put("Time in Hospital", "admissions.admittime/dischtime");
        sources.put("Payer Code", "admissions.insurance");
        sources.put("Medical Specialty", "services.curr_service");
        sources.put("Num Lab Procedures", "labevents (count per encounter)");
        sources.put("Num Procedures", "procedures_icd (count per encounter)");
        sources.put("Num Medications", "prescriptions (distinct drugs per encounter)");
        sources.put("Number Diagnoses", "diagnoses_icd (count per encounter)");
        sources.put("Number Outpatient", "admissions (observation stays, prior year)");
        sources.put("Number Emergency", "admissions (emergency stays, prior year)");
        sources.put("Number Inpatient", "admissions (prior year)");
        sources.put("Diag 1", "diagnoses_icd (seq_num 1)");
        sources.put("Diag 2", "diagnoses_icd (seq_num 2)");
        sources.put("Diag 3", "diagnoses_icd (seq_num 3)");
        sources.put("A1C Result", "labevents (itemid 50852)");
        sources.put("Max Glu Serum", "labevents (itemid 50931/50809)");
        for (String agent : List.of("Metformin", "Repaglinide", "Nateglinide", "Chlorpropamide",
                "Glimepiride", "Acetohexamide", "Glipizide", "Glyburide", "Tolbutamide",
                "Pioglitazone", "Rosiglitazone", "Acarbose", "Miglitol", "Troglitazone",
                "Tolazamide", "Examide", "Citoglipton", "Insulin", "Glyburide-Metformin",
                "Glipizide-Metformin", "Glimepiride-Pioglitazone", "Metformin-Rosiglitazone",
                "Metformin-Pioglitazone")) {
            sources.put(agent, "prescriptions.drug");
        }
        sources.put("Change", "prescriptions (derived)");
        sources.put("Diabetes Med", "prescriptions (derived)");
        sources.put("Readmitted", "admissions (derived)");
        return sources;
    }
}
