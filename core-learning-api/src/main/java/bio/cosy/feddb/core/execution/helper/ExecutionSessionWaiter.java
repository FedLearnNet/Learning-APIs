package bio.cosy.feddb.core.execution.helper;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Polls a probe until it yields a value, fails, or runs out of time.
 *
 * <p>Meant for state that is owned elsewhere and only observable by asking - a websocket that
 * has to connect, a step that has to reach a status. Push-based results (a future someone
 * completes) do not need this.</p>
 *
 * <p>An exception thrown by the probe ends the wait immediately and is passed on as is, so a
 * probe can fail fast once the awaited state can no longer be reached. The first probe runs on
 * the subscribing thread, later ones on the Mutiny worker pool.</p>
 *
 * <pre>{@code
 * WebSocketConnection connection = ExecutionSessionWaiter
 *         .until("app 'x' (step 5) to connect", () -> findConnection(5L))
 *         .atMost(Duration.ofMinutes(2))
 *         .onTimeout(() -> new IllegalStateException("app never connected"))
 *         .await();
 * }</pre>
 *
 * @param <T> what the probe yields once the awaited state is reached
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public final class ExecutionSessionWaiter<T> {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_POLL = Duration.ofMillis(250);
    private static final Duration DEFAULT_LOG_INTERVAL = Duration.ofSeconds(15);

    private Supplier<Optional<T>> probe;

    private String description = "ExecutionSessionWaiter";
    private Duration timeout = DEFAULT_TIMEOUT;
    private Duration pollInterval = DEFAULT_POLL;
    private Duration logInterval = DEFAULT_LOG_INTERVAL;
    private Supplier<? extends RuntimeException> onTimeout = () -> new IllegalStateException(
            new TimeoutException("Timed out after " + timeout.toSeconds() + "s waiting for " + description));

    private ExecutionSessionWaiter(Supplier<Optional<T>> probe) {
        this.probe = probe;
    }

    private ExecutionSessionWaiter(Supplier<Optional<T>> probe, String description) {
        this.probe = probe;
        this.description = description;
    }

    public Uni<T> asUni() {
        return Uni.createFrom().item(this::probe)
                .repeat().withDelay(pollInterval).whilst(Optional::isEmpty)
                .select().where(Optional::isPresent)
                .toUni()
                .map(Optional::get)
                .ifNoItem().after(timeout).failWith(onTimeout);
    }

    /**
     * Blocks the calling thread; never call it from an event-loop thread.
     */
    public T await() {
        return asUni().await().indefinitely();
    }

    public Optional<T> probe() {
        return probe(new ExecutionSessionProgress());
    }

    public Optional<T> probe(ExecutionSessionProgress progress) {
        Optional<T> result = probe.get();
        long now = System.nanoTime();
        if (result.isPresent()) {
            if (progress.isWaited()) {
                Log.infof("Done waiting for %s after %ds", description, progress.elapsedSeconds(now));
            }
            return result;
        }
        // Logged on the first miss only, so a state that is already reached stays silent.
        if (!progress.isWaited()) {
            progress.setWaited(true);
            progress.setNextLog(now + logInterval.toNanos());
            Log.infof("Waiting up to %ds for %s", timeout.toSeconds(), description);
        } else if (now >= progress.getNextLog()) {
            progress.setNextLog(now + logInterval.toNanos());
            Log.infof("Still waiting for %s after %ds", description, progress.elapsedSeconds(now));
        }
        return result;
    }
}
