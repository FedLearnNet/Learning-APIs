package eval;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.privacy.PrivacyBO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.query.QueryResultDTO;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//Make sure mimic-iv-full-2.2-slim.zip is there and processed via preprocessing/build_connector_zip.py
@QuarkusTest
@TestProfile(MimicQueryEvalTest.MimicQueryProfile.class)
public class MimicQueryEvalTest {

    /** Supplies the dev-only {@code mimic} group and the eval disclosure floor. */
    public static class MimicQueryProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    // Importing the full MIMIC connector keeps a single test method busy for the
                    // better part of an hour, which the 10 minute default reports as a hang. The
                    // override lives in the profile so only this test relaxes the detector.
                    Map.entry("quarkus.test.hang-detection-timeout", "6h"),
                    Map.entry("quarkus.oidc.enabled", "false"),
                    Map.entry("flnet.global.socket.enabled", "false"),
                    Map.entry("flnet.auto-subscribe.mimic.global-schema-id",
                            "bef52fc9-faed-44b4-b3ac-e786b4731bcf"),
                    Map.entry("flnet.auto-subscribe.mimic.cohort-name", COHORT_NAME),
                    Map.entry("flnet.auto-subscribe.mimic.etl-data-path",
                            "/connectors/mimic-iv-full-2.2-slim.zip"),
                    Map.entry("flnet.auto-subscribe.mimic.connector-path",
                            "/connectors/mimic-iv-demo-2.2.json"),
                    Map.entry("flnet.auto-subscribe.mimic.add-for-user", "test"));
        }
    }

    /**
     * Covers uploading the multi-gigabyte archive into the large object store, parsing every ZIP
     * entry to a disk-backed table and profiling all of their rows - roughly 15 million for the
     * largest table alone. Matched to the {@code @TransactionConfiguration} budget on
     * {@code AutoSubscriberBO#ensureConnector}, since that transaction wraps the whole thing and a
     * shorter await here only fails the test while the import keeps running.
     */
    private static final Duration SUBSCRIBE_TIMEOUT = Duration.ofHours(4);

    private static final String GROUP_KEY = "mimic";
    static final String COHORT_NAME = "MIMIC IV Demo Schema";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";

    // Harmonised schema-node names — identical to QueryEvalTest.
    private static final String RACE = "Race";
    private static final String GENDER = "Gender";
    private static final String A1C = "A1C Result";
    private static final String DIABETES_MED = "Diabetes Med";
    private static final String METFORMIN = "Metformin";
    private static final String INSULIN = "Insulin";
    private static final String TIME_IN_HOSPITAL = "Time in Hospital";
    private static final String READMITTED = "Readmitted";

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    PatientAO patientAO;

    @Inject
    SchemaNodeAO schemaNodeAO;

    @Inject
    QueryBO queryBO;

    @Inject
    PrivacyBO privacyBO;

    @Inject
    FLNetClientConfig config;

    @Inject
    ObjectMapper objectMapper;

    /** Resolved local identifiers for a single schema node. */
    private record SchemaRef(String ontologyGlobalId, String dataTypeGlobalId) {
    }

    private Long cohortId;
    private Map<String, SchemaRef> schemaByName;

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
    void evaluateSeedQueriesOnMimic() throws Exception {
        // ---- 1) Run the mimic connector ----
        ensureMimicImported();

        // ---- 2) Resolve schema-node names -> local ontology / datatype ids ----
        schemaByName = resolveSchemaRefs(cohortId);
        for (String name : List.of(RACE, GENDER, A1C, DIABETES_MED, METFORMIN, INSULIN,
                TIME_IN_HOSPITAL, READMITTED)) {
            assertTrue(schemaByName.containsKey(name),
                    "Schema node '" + name + "' should exist after import. Found: " + schemaByName.keySet());
        }

        // ---- 3) The same seed queries as the US-130 evaluation ----
        Map<String, QueryDTO> queries = new LinkedHashMap<>();
        queries.put("all_female", query(item(GENDER, QueryOperatorTypes.EQUAL, "Female")));
        queries.put("race_caucasian", query(item(RACE, QueryOperatorTypes.EQUAL, "Caucasian")));
        queries.put("a1c_not_measured", query(item(A1C, QueryOperatorTypes.EQUAL, "None")));
        queries.put("on_diabetes_meds", query(item(DIABETES_MED, QueryOperatorTypes.EQUAL, "Yes")));
        queries.put("metformin_steady", query(item(METFORMIN, QueryOperatorTypes.EQUAL, "Steady")));
        queries.put("insulin_steady", query(item(INSULIN, QueryOperatorTypes.EQUAL, "Steady")));
        queries.put("long_stay", query(item(TIME_IN_HOSPITAL, QueryOperatorTypes.BIGGER, "7")));
        queries.put("readmit_30", query(item(READMITTED, QueryOperatorTypes.EQUAL, "<30")));
        queries.put("caucasian_female", query(
                item(RACE, QueryOperatorTypes.EQUAL, "Caucasian"),
                item(GENDER, QueryOperatorTypes.EQUAL, "Female")));
        queries.put("a1c_high_metformin_up", query(
                item(A1C, QueryOperatorTypes.EQUAL, ">8"),
                item(METFORMIN, QueryOperatorTypes.EQUAL, "Up")));
        queries.put("race_asian", query(item(RACE, QueryOperatorTypes.EQUAL, "Asian")));
        queries.put("aa_insulin_up", query(
                item(RACE, QueryOperatorTypes.EQUAL, "AfricanAmerican"),
                item(INSULIN, QueryOperatorTypes.EQUAL, "Up")));
        queries.put("asian_male_a1c8", query(
                item(RACE, QueryOperatorTypes.EQUAL, "Asian"),
                item(GENDER, QueryOperatorTypes.EQUAL, "Male"),
                item(A1C, QueryOperatorTypes.EQUAL, ">8")));

        // ---- 4) Evaluate raw + released (released computed 3x for reproducibility) ----
        StringBuilder reproducibility = new StringBuilder("Query Key,Released r1,Released r2,Released r3,Identical\n");

        for (Map.Entry<String, QueryDTO> entry : queries.entrySet()) {
            String key = entry.getKey();
            long raw = rawCount(entry.getValue());

            long r1 = privacyBO.modifyQueryCount(raw);
            long r2 = privacyBO.modifyQueryCount(raw);
            long r3 = privacyBO.modifyQueryCount(raw);
            boolean identical = (r1 == r2) && (r2 == r3);

            reproducibility.append(String.format("%s,%d,%d,%d,%s%n",
                    key, r1, r2, r3, identical ? "Yes" : "No"));
        }

        // ---- 5) Emit both CSVs to console and disk ----
        printCsv("Released reproducibility (disclosure-control determinism)", reproducibility.toString());

        Path outDir = Path.of("target", "query-eval");
        Files.createDirectories(outDir);
        Path reproFile = outDir.resolve("mimic-released-reproducibility.csv");
        Path rawFile = outDir.resolve("mimic-raw-vs-released.csv");
        Files.writeString(reproFile, reproducibility.toString(), StandardCharsets.UTF_8);
        Files.writeString(rawFile, reproducibility.toString(), StandardCharsets.UTF_8);
        System.out.println("[MIMIC-QUERY-EVAL] Saved: " + reproFile.toAbsolutePath());
        System.out.println("[MIMIC-QUERY-EVAL] Saved: " + rawFile.toAbsolutePath());
    }

    // ------------------------------------------------------------------
    // Setup helpers
    // ------------------------------------------------------------------

    /**
     * Runs the mimic auto-subscribe flow (cohort + connector, ZIP upload, run)
     * unless the cohort already exists with imported patients. Only the mimic
     * group is subscribed — the {@code %test} profile also configures us-130, and
     * importing it here would neither be needed nor free.
     * <p>
     * The connector run is asynchronous, and the imported patient count is used as
     * the completion signal rather than the run's {@code FINISHED} status: that
     * transition is coupled to an SSE/websocket {@code sendUpdate} which can fail
     * in the test environment even though the data loaded successfully.
     */
    private void ensureMimicImported() {
        Optional<Long> existing = QuarkusTransaction.requiringNew()
                .call(() -> cohortAO.findByName(COHORT_NAME).map(CohortEntity::getId));
        if (existing.isPresent()
                && QuarkusTransaction.requiringNew().call(() -> patientAO.countByCohortId(existing.get())) > 0) {
            cohortId = existing.get();
            return;
        }

        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = config.autoSubscribe().groups().get(GROUP_KEY);
        assertNotNull(groupConfig, "Auto-subscribe group '" + GROUP_KEY + "' must be configured for this profile");

        Boolean ok = autoSubscriberBO
                .subscribeToFLNet(GROUP_KEY, groupConfig)
                .await().atMost(SUBSCRIBE_TIMEOUT);
        assertTrue(Boolean.TRUE.equals(ok), "AutoSubscribe for group '" + GROUP_KEY + "' should succeed");

        cohortId = QuarkusTransaction.requiringNew()
                .call(() -> cohortAO.findByName(COHORT_NAME).map(CohortEntity::getId))
                .orElseThrow(() -> new AssertionError("Cohort '" + COHORT_NAME + "' was not created"));

        // 27 merged source tables (~1.05M rows) are streamed patient by patient
        // through transform, mapping and load; allow a generous budget.
        long patients = awaitStablePatientCount(cohortId, 3_600_000);
        assertTrue(patients > 0, "Patients should have been imported into the cohort within the timeout");
        System.out.println("[MIMIC-QUERY-EVAL] Imported " + patients + " distinct patients into cohort " + cohortId);
    }

    /**
     * Polls the imported patient count until it is non-zero and unchanged across
     * several consecutive reads. One patient group is roughly ten thousand merged
     * rows, so the quiet period is deliberately long enough not to mistake a slow
     * patient for the end of the import.
     */
    private long awaitStablePatientCount(Long cohortId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long previous = -1;
        int stableReads = 0;
        long current = 0;
        while (System.currentTimeMillis() < deadline) {
            current = QuarkusTransaction.requiringNew().call(() -> patientAO.countByCohortId(cohortId));
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

    /**
     * Reads the persisted schema nodes for the cohort and maps each node name to
     * the local ontology/datatype global ids that {@link QueryBO} resolves on.
     */
    private Map<String, SchemaRef> resolveSchemaRefs(Long cohortId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Map<String, SchemaRef> refs = new HashMap<>();
            for (SchemaNodeEntity node : schemaNodeAO.getSchemaNodesForCohort(cohortId)) {
                if (node.getOntology() == null || node.getDataType() == null) {
                    continue; // group / structural nodes carry no ontology
                }
                refs.put(node.getName(), new SchemaRef(
                        node.getOntology().getGlobalId(),
                        node.getDataType().getGlobalDataTypeId()));
            }
            return refs;
        });
    }

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    private QueryItemDTO item(String schemaName, QueryOperatorTypes operator, String value) {
        SchemaRef ref = schemaByName.get(schemaName);
        assertNotNull(ref, "No resolved schema reference for '" + schemaName + "'");

        QueryOperatorDTO op = new QueryOperatorDTO();
        op.setOperator(operator);
        op.setValue(value);

        QueryItemDTO queryItem = new QueryItemDTO();
        queryItem.setOntologyId(ref.ontologyGlobalId());
        queryItem.setDataTypeId(ref.dataTypeGlobalId());
        queryItem.setOperator(List.of(op));
        return queryItem;
    }

    private QueryDTO query(QueryItemDTO... items) {
        QueryDTO dto = new QueryDTO();
        dto.setQuery(List.of(items));
        return dto;
    }

    /** Runs the query against the MIMIC-IV cohort and returns the raw distinct-patient count. */
    private long rawCount(QueryDTO query) {
        QueryResultWrapperDTO result = queryBO.runQuery(query, List.of(cohortId));
        if (result == null || result.getResult() == null) {
            return 0L;
        }
        return result.getResult().stream()
                .mapToLong(QueryResultDTO::getPatientCount)
                .sum();
    }

    private void printCsv(String title, String csv) {
        System.out.println("\n[MIMIC-QUERY-EVAL] " + title + "\n" + csv);
    }
}
