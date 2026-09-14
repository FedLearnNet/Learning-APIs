package bio.cosy.feddb.local.api.importer;

import io.quarkus.logging.Log;

import java.util.concurrent.TimeUnit;

/**
 * Stopwatch for the import phase logs.
 *
 * <p>Emits a {@code start} line when created and a {@code done} line with the elapsed time when
 * finished, so the cost of upload, parsing, statistics and cache lookups can be read straight off a
 * run's logs instead of being inferred from timestamps. Mirrors the {@code ETL [Step]} convention
 * already used by the run pipeline.</p>
 *
 */
public final class ImportPhaseTimer {

    private final String phase;
    private final long startedNanos;

    private ImportPhaseTimer(String phase) {
        this.phase = phase;
        this.startedNanos = System.nanoTime();
    }

    public static ImportPhaseTimer started(String phase) {
        Log.infof("IMPORT [%s] start", phase);
        return new ImportPhaseTimer(phase);
    }

    /**
     * Starts the phase and announces what it is about to work on, so a long-running phase can be
     * attributed to a concrete input while it is still running rather than only once it is done.
     */
    public static ImportPhaseTimer started(String phase, String detailFormat, Object... args) {
        String detail = String.format(detailFormat, args);
        Log.infof("IMPORT [%s] start - %s", phase, detail);
        return new ImportPhaseTimer(phase);
    }

    /** Starts the phase without a log line, for phases too small to be worth announcing twice. */
    public static ImportPhaseTimer silent(String phase) {
        return new ImportPhaseTimer(phase);
    }

    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    public void done() {
        long elapsed = elapsedMillis();
        Log.infof("IMPORT [%s] done in %d ms", phase, elapsed);
    }

    public void done(String detailFormat, Object... args) {
        long elapsed = elapsedMillis();
        String detail = String.format(detailFormat, args);
        Log.infof("IMPORT [%s] done in %d ms - %s", phase, elapsed, detail);
    }

    public void failed(Throwable throwable) {
        long elapsed = elapsedMillis();
        Log.warnf(throwable, "IMPORT [%s] failed after %d ms", phase, elapsed);
    }
}
