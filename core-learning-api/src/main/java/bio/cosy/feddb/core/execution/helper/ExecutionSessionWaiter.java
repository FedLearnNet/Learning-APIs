package bio.cosy.feddb.core.execution.helper;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
public final class ExecutionSessionWaiter<T> {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_POLL = Duration.ofMillis(250);
    private static final Duration DEFAULT_LOG_INTERVAL = Duration.ofSeconds(15);

    private final String description;
    private final Supplier<Optional<T>> probe;
    private Duration timeout = DEFAULT_TIMEOUT;
    private Duration pollInterval = DEFAULT_POLL;
    private Duration logInterval = DEFAULT_LOG_INTERVAL;
    private Supplier<? extends RuntimeException> onTimeout;

    private ExecutionSessionWaiter(String description, Supplier<Optional<T>> probe) {
        this.description = Objects.requireNonNull(description, "description");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.onTimeout = () -> new IllegalStateException(
                new TimeoutException("Timed out after " + timeout.toSeconds() + "s waiting for " + description));
    }

    /**
     * @param description what is awaited, phrased to follow "waiting for", used in log lines
     * @param probe       empty while the state is not reached yet; may throw to abort the wait
     */
    public static <T> ExecutionSessionWaiter<T> until(String description, Supplier<Optional<T>> probe) {
        return new ExecutionSessionWaiter<>(description, probe);
    }

    public ExecutionSessionWaiter<T> atMost(Duration timeout) {
        this.timeout = requirePositive(timeout, "timeout");
        return this;
    }

    public ExecutionSessionWaiter<T> pollEvery(Duration pollInterval) {
        this.pollInterval = requirePositive(pollInterval, "pollInterval");
        return this;
    }

    public ExecutionSessionWaiter<T> logEvery(Duration logInterval) {
        this.logInterval = requirePositive(logInterval, "logInterval");
        return this;
    }

    /** Failure raised when the timeout elapses; also the place for cleanup such as failing a step. */
    public ExecutionSessionWaiter<T> onTimeout(Supplier<? extends RuntimeException> onTimeout) {
        this.onTimeout = Objects.requireNonNull(onTimeout, "onTimeout");
        return this;
    }

    public Uni<T> asUni() {
        // Settings are captured here so a later change to this instance cannot affect a running wait.
        Supplier<Optional<T>> probe = this.probe;
        String description = this.description;
        Duration timeout = this.timeout;
        Duration logInterval = this.logInterval;
        Progress progress = new Progress();

        return Uni.createFrom().item(() -> probe(probe, description, timeout, logInterval, progress))
                .repeat().withDelay(pollInterval).whilst(Optional::isEmpty)
                .select().where(Optional::isPresent)
                .toUni()
                .map(Optional::get)
                .ifNoItem().after(timeout).failWith(onTimeout);
    }

    /** Blocks the calling thread; never call it from an event-loop thread. */
    public T await() {
        return asUni().await().indefinitely();
    }

    private static <T> Optional<T> probe(Supplier<Optional<T>> probe,
                                         String description,
                                         Duration timeout,
                                         Duration logInterval,
                                         Progress progress) {
        Optional<T> result = Objects.requireNonNullElse(probe.get(), Optional.empty());
        long now = System.nanoTime();
        if (result.isPresent()) {
            if (progress.waited) {
                Log.infof("Done waiting for %s after %ds", description, progress.elapsedSeconds(now));
            }
            return result;
        }
        // Logged on the first miss only, so a state that is already reached stays silent.
        if (!progress.waited) {
            progress.waited = true;
            progress.nextLog = now + logInterval.toNanos();
            Log.infof("Waiting up to %ds for %s", timeout.toSeconds(), description);
        } else if (now >= progress.nextLog) {
            progress.nextLog = now + logInterval.toNanos();
            Log.infof("Still waiting for %s after %ds", description, progress.elapsedSeconds(now));
        }
        return result;
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, was " + duration);
        }
        return duration;
    }

    /** Per-wait log state; probes of one wait never overlap, volatile only covers the thread hops. */
    private static final class Progress {
        private final long started = System.nanoTime();
        private volatile boolean waited;
        private volatile long nextLog;

        private long elapsedSeconds(long now) {
            return TimeUnit.NANOSECONDS.toSeconds(now - started);
        }
    }
}
