package eval;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.permission.AutoMetricsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoStatisticsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoTrainingAccess;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
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

import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluation test for data harmonization across heterogeneous sites (paper
 * requirements RE1 — built-in harmonization, RE2 — shared schema).
 * <p>
 * One reference site exports the canonical UCI US-130 layout; four further sites
 * export the <em>same</em> clinical content after a different, deliberately
 * awkward local transformation (see {@code resources/connectors/preprocessing/}):
 * <ol>
 *   <li><b>site&nbsp;1</b> — canonical reference ({@code clinic_001.csv}).</li>
 *   <li><b>site&nbsp;2</b> — numeric coding systems (HL7 gender, in-house race
 *       codebook, ordinal severity/dosage codes), renamed and reordered.</li>
 *   <li><b>site&nbsp;3</b> — semicolon-delimited European export, German category
 *       vocabulary, length-of-stay in hours.</li>
 *   <li><b>site&nbsp;4</b> — ISO-8601 duration length-of-stay, typographic dosage
 *       symbols, bracket-free age ranges, terse lab/outcome tokens.</li>
 *   <li><b>site&nbsp;5</b> — messy free-text extract: inconsistent casing, stray
 *       whitespace, heterogeneous missing-value sentinels.</li>
 * </ol>
 * Each site is onboarded through the standard FL-Net workflow — a declarative
 * connector (transformer chain + schema mapping) and the shared global schema
 * (mocked here from {@code /connectors/global_schema_dummy.json}). The test
 * asserts that every heterogeneous subset reaches a complete, schema-valid local
 * representation <em>through configuration alone</em>: the import runs without
 * error, patients are loaded, and the expected shared-schema fields are present.
 * A harmonization report (printed and saved under {@code target/}) summarises,
 * per site, the rows imported, fields mapped, and values rejected during the
 * import's schema validation.
 */
@QuarkusTest
@TestProfile(HarmonizationEvalTest.HarmonizationProfile.class)
public class HarmonizationEvalTest {

    /**
     * Onboarding and harmonization are exercised entirely through the local BOs,
     * so this evaluation needs no external identity provider. The profile disables
     * OIDC and the startup global websocket so the test boots self-contained
     * (no network to the auth server), which also makes it CI-reproducible.
     */
    public static class HarmonizationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc.enabled", "false",
                    "flnet.global.socket.enabled", "false");
        }
    }

    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";

    /** A simulated site: its heterogeneous export plus the connector that harmonises it. */
    private record Site(int id, String key, String cohortName, String csvPath,
                        String connectorPath, String description) {
    }

    private static final List<Site> SITES = List.of(
            new Site(1, "us130-site-1", "US 130 Site 1",
                    "/connectors/clinic_001.csv", "/connectors/us-130-clinics.json",
                    "Canonical reference layout"),
            new Site(2, "us130-site-2", "US 130 Site 2",
                    "/connectors/site_02.csv", "/connectors/us-130-site-02.json",
                    "Numeric coding systems (HL7 gender, race codebook, ordinal codes)"),
            new Site(3, "us130-site-3", "US 130 Site 3",
                    "/connectors/site_03.csv", "/connectors/us-130-site-03.json",
                    "Semicolon-delimited German export, length-of-stay in hours"),
            new Site(4, "us130-site-4", "US 130 Site 4",
                    "/connectors/site_04.csv", "/connectors/us-130-site-04.json",
                    "ISO-8601 duration, symbolic dosage codes, bracket-free ages"),
            new Site(5, "us130-site-5", "US 130 Site 5",
                    "/connectors/site_05.csv", "/connectors/us-130-site-05.json",
                    "Messy casing/whitespace and heterogeneous missing sentinels")
    );

    /**
     * Shared-schema fields that every site must end up exposing once harmonised.
     * These span every transformation family exercised by the four heterogeneous
     * sites (categorical recodes, unit/temporal rescales, missing-value handling).
     */
    private static final List<String> REQUIRED_FIELDS = List.of(
            "Race", "Gender", "Age", "A1C Result", "Max Glu Serum",
            "Diabetes Med", "Change", "Readmitted", "Metformin", "Insulin",
            "Time in Hospital", "Num Lab Procedures");

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
    void harmonizeHeterogeneousSites() throws Exception {
        StringBuilder report = new StringBuilder(
                "Site,Description,Source File,Source Columns,Rows Imported,Fields Mapped,Value Rejections/Coercions,Status\n");

        for (Site site : SITES) {
            HarmonizationOutcome outcome = onboard(site);

            // RE1/RE2: a complete, schema-valid local representation through config alone.
            assertTrue(outcome.patients() > 0,
                    "Site " + site.id() + " (" + site.description() + ") should import patients");
            for (String field : REQUIRED_FIELDS) {
                assertTrue(outcome.mappedFields().contains(field),
                        "Site " + site.id() + " must expose shared-schema field '" + field
                                + "' after harmonization. Mapped: " + outcome.mappedFields());
            }
            // RE1: harmonization is lossless — no value is rejected or coerced
            // against the shared schema once the connector has run.
            assertEquals(0, outcome.rejections(),
                    "Site " + site.id() + " (" + site.description()
                            + ") should harmonise with no value rejections/coercions");

            report.append(String.format("Site %d,%s,%s,%d,%d,%d,%d,%s%n",
                    site.id(), site.description(), site.csvPath().substring(site.csvPath().lastIndexOf('/') + 1),
                    outcome.sourceColumns(), outcome.patients(), outcome.mappedFields().size(),
                    outcome.rejections(), "OK"));

            System.out.printf("[HARMONIZATION] Site %d (%s): %d rows, %d fields mapped (%d required present), "
                            + "%d value rejections/coercions%n",
                    site.id(), site.description(), outcome.patients(), outcome.mappedFields().size(),
                    REQUIRED_FIELDS.size(), outcome.rejections());
        }

        printReport(report.toString());
        Path outDir = Path.of("target", "harmonization-eval");
        Files.createDirectories(outDir);
        Path reportFile = outDir.resolve("harmonization-report.csv");
        Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
        System.out.println("[HARMONIZATION] Saved: " + reportFile.toAbsolutePath());
    }

    // ------------------------------------------------------------------
    // Onboarding
    // ------------------------------------------------------------------

    private record HarmonizationOutcome(long patients, Set<String> mappedFields,
                                        int sourceColumns, long rejections) {
    }

    /**
     * Runs one site through the standard auto-subscribe onboarding: subscribe to
     * the (mocked) shared schema, create the cohort, load the declarative
     * connector, attach the heterogeneous CSV, and execute the connector run.
     * Returns the harmonization outcome read back from the persisted state.
     */
    private HarmonizationOutcome onboard(Site site) {
        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = groupConfigFor(site);
        String keycloakId = groupConfig.addForUser().orElseGet(() -> config.user().systemUserName());

        ConnectorDTO connector = autoSubscriberBO.prepareSubscription(site.key(), groupConfig, keycloakId);
        assertNotNull(connector, "Connector for site " + site.id() + " should be created");

        ConnectorRunDTO run = connectorRunBO.startRun(connector.getId(), true, false, keycloakId);
        assertNotNull(run, "Connector run for site " + site.id() + " should start");

        Long cohortId = QuarkusTransaction.requiringNew()
                .call(() -> cohortAO.findByName(site.cohortName()).map(CohortEntity::getId))
                .orElseThrow(() -> new AssertionError("Cohort '" + site.cohortName() + "' was not created"));

        long patients = awaitStablePatientCount(cohortId, 600_000);
        Set<String> mappedFields = resolveMappedFields(cohortId);
        int sourceColumns = countSourceColumns(site);
        long rejections = QuarkusTransaction.requiringNew()
                .call(() -> patientLogBO.getByRunId(run.getId(), null, null).stream()
                        .filter(log -> isValueRejection(log.getMessage()))
                        .count());

        return new HarmonizationOutcome(patients, mappedFields, sourceColumns, rejections);
    }

    /**
     * True iff an import log records a <em>value</em> being rejected or coerced
     * during schema validation (bad type, out-of-domain category, required value
     * missing). Structural notices — most importantly the "atomic attribute has
     * multiple entries" message emitted because the dataset carries several
     * encounters per patient — are not value problems and are excluded, so the
     * count reflects genuine harmonization defects only.
     */
    private boolean isValueRejection(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("is not allowed")
                || m.contains("cannot be normalized")
                || m.contains("is required")
                || m.contains("out of range")
                || m.contains("does not match required pattern");
    }

    /**
     * Builds an auto-subscribe group config for a site, delegating all defaults
     * (schema id, permissions, query settings) to the configured {@code us-130}
     * group and overriding only the cohort name, data file and connector.
     */
    private FLNetClientConfig.AutoSubscribeGroupConfig groupConfigFor(Site site) {
        FLNetClientConfig.AutoSubscribeGroupConfig base = config.autoSubscribe().groups().get("us-130");
        assertNotNull(base, "Base auto-subscribe group 'us-130' must be configured for the test profile");
        return new FLNetClientConfig.AutoSubscribeGroupConfig() {
            @Override
            public String globalSchemaId() {
                return base.globalSchemaId();
            }

            @Override
            public String cohortName() {
                return site.cohortName();
            }

            @Override
            public Optional<String> etlDataPath() {
                return Optional.of(site.csvPath());
            }

            @Override
            public Optional<String> connectorPath() {
                return Optional.of(site.connectorPath());
            }

            @Override
            public Optional<String> addForUser() {
                return Optional.empty();
            }

            @Override
            public Boolean useDefaultCohortPermission() {
                return true;
            }

            @Override
            public FLNetClientConfig.DefaultCohortPermission defaultCohortPermission() {
                return config.cohort().defaultCohortPermission();
            }

            @Override
            public Boolean fileWatching() {
                return false;
            }
        };
    }

    /**
     * Polls the imported patient count until it is non-zero and unchanged across
     * three consecutive reads — the connector run is asynchronous, so once the
     * count stops growing the import has settled.
     */
    private long awaitStablePatientCount(Long cohortId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long previous = -1;
        int stableReads = 0;
        while (System.currentTimeMillis() < deadline) {
            long current = QuarkusTransaction.requiringNew().call(() -> patientAO.countByCohortId(cohortId));
            if (current > 0 && current == previous) {
                if (++stableReads >= 2) {
                    return current;
                }
            } else {
                stableReads = 0;
            }
            previous = current;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return current;
            }
        }
        return previous;
    }

    /** Reads back the shared-schema field names that resolved to an ontology + datatype. */
    private Set<String> resolveMappedFields(Long cohortId) {
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

    /** Counts the columns in a site's heterogeneous export (delimiter-aware). */
    private int countSourceColumns(Site site) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(site.csvPath().substring(1))) {
            if (in == null) {
                return 0;
            }
            String header = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
            char delimiter = header.indexOf(';') >= 0 ? ';' : ',';
            return header.isEmpty() ? 0 : (int) header.chars().filter(c -> c == delimiter).count() + 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private void printReport(String csv) {
        System.out.println("\n[HARMONIZATION] Harmonization report (heterogeneous sites -> shared schema)\n" + csv);
    }
}
