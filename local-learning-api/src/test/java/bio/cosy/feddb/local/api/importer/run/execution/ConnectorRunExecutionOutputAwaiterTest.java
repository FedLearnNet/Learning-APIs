package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.local.api.importer.files.table.TableData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorRunExecutionOutputAwaiterTest {

    private final ConnectorRunExecutionOutputAwaiter awaiter = new ConnectorRunExecutionOutputAwaiter();

    @Test
    void failedBatchFailsAtOnceWithTheAppError() {
        awaiter.registerBatch(-3L, 34L);
        assertTrue(awaiter.isBatch(-3L));

        awaiter.failBatch(-3L, "KeyError: 'Rx_insulin'");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> awaiter.awaitBatch(-3L, Duration.ofMinutes(5)).await().atMost(Duration.ofSeconds(1)));
        assertEquals("KeyError: 'Rx_insulin'", error.getMessage());
        assertFalse(awaiter.isBatch(-3L));
    }

    @Test
    void batchesOfADisconnectedAppFailAtOnce() {
        awaiter.registerBatch(-5L, 34L);
        awaiter.registerBatch(-6L, 35L);

        awaiter.failBatchesOf(34L, "frame exceeds limit of 1048576 bytes");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> awaiter.awaitBatch(-5L, Duration.ofMinutes(5)).await().atMost(Duration.ofSeconds(1)));
        assertEquals("frame exceeds limit of 1048576 bytes", error.getMessage());
        assertTrue(awaiter.isBatch(-6L));
    }

    @Test
    void resultThatArrivesBeforeTheCallerWaitsIsNotLost() {
        awaiter.registerBatch(-4L, 34L);
        TableData output = new TableData(List.of("value"), List.of(), null);

        assertTrue(awaiter.completeBatch(-4L, output));

        assertSame(output, awaiter.awaitBatch(-4L, Duration.ofMinutes(5)).await().atMost(Duration.ofSeconds(1)));
        assertFalse(awaiter.isBatch(-4L));
    }
}
