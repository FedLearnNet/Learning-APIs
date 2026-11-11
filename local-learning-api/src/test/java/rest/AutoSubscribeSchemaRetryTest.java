package rest;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rest.helper.AutoSubscribeTestHelper;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group whose global schema does not exist yet is retried by a scheduled job until the schema
 * appears, after which the job unregisters itself.
 */
@QuarkusTest
public class AutoSubscribeSchemaRetryTest {

    private static final String RETRY_JOB = "auto-subscribe-schema-retry";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    Scheduler scheduler;

    @Inject
    ObjectMapper objectMapper;

    private final AtomicBoolean schemaAvailable = new AtomicBoolean(false);
    private final AtomicInteger subscribeCalls = new AtomicInteger();

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
                subscribeCalls.incrementAndGet();
                if (!schemaAvailable.get()) {
                    return Uni.createFrom().failure(new WebApplicationException(Response.Status.NOT_FOUND));
                }
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
    void subscribeToFLNet_schemaMissing_retriesUntilAvailable() throws Exception {
        String cohortName = "AutoSub_Retry_" + System.currentTimeMillis();
        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = AutoSubscribeTestHelper.buildGroupConfig(
                cohortName, Optional.empty(), Optional.empty(), false);

        Boolean ok = autoSubscriberBO.subscribeToFLNet(Map.of("retry-group", groupConfig))
                .await().atMost(Duration.ofSeconds(15));

        assertFalse(ok, "First pass should report the group as not subscribed");
        assertEquals(1, subscribeCalls.get(), "First pass should try the schema exactly once");
        assertNotNull(scheduler.getScheduledJob(RETRY_JOB), "Retry job should be registered");
        assertFalse(cohortExists(cohortName), "The cohort of the failed attempt must be rolled back");

        schemaAvailable.set(true);

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && !cohortExists(cohortName)) {
            Thread.sleep(500);
        }
        assertTrue(cohortExists(cohortName), "Retry job should create the cohort once the schema exists");

        deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && scheduler.getScheduledJob(RETRY_JOB) != null) {
            Thread.sleep(200);
        }
        assertNull(scheduler.getScheduledJob(RETRY_JOB), "Retry job should unregister once nothing is pending");
    }

    private boolean cohortExists(String cohortName) {
        return QuarkusTransaction.requiringNew().call(() -> cohortAO.findByName(cohortName).isPresent());
    }
}
