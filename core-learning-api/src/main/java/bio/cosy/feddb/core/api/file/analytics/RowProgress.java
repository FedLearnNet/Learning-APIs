package bio.cosy.feddb.core.api.file.analytics;

import io.quarkus.logging.Log;

import java.util.concurrent.TimeUnit;

/**
 * Says how far a row scan has come, in the import log and to whoever is watching the import.
 *
 * <p>Profiling is the longest phase of a large import, so it has to report while it runs - but from a
 * path that executes per row. The check is therefore a masked counter first and a clock read only
 * every so often. Both profilers report the same way, which is why this lives here rather than in
 * each of them.</p>
 *
 * <p>The log and the listener are on different clocks on purpose: a line every half minute is enough
 * to read a finished import off the log, and a screen that only moved that often would look stuck.</p>
 */
final class RowProgress {

    /** How often a long scan reports to the log. */
    private static final long LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    /** How often it reports to whoever is watching it happen. */
    private static final long LISTEN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);

    /** Rows between clock reads; a power-of-two mask keeps the check off the per-row hot path. */
    private static final long CHECK_MASK = (1L << 16) - 1L;

    private final String phase;
    private final String source;
    private final String detail;
    private final RowScanListener listener;

    private final long startedNanos = System.nanoTime();
    private long nextLogNanos = startedNanos + LOG_INTERVAL_NANOS;
    private long nextListenNanos = startedNanos + LISTEN_INTERVAL_NANOS;

    /** @param detail appended to the log line, e.g. how many columns and threads are doing the work */
    RowProgress(String phase, String source, String detail, RowScanListener listener) {
        this.phase = phase;
        this.source = source;
        this.detail = detail == null ? "" : detail;
        this.listener = listener == null ? RowScanListener.NONE : listener;
    }

    /** Records that a row is done. Costs a masked comparison on all but every 65,536th row. */
    void rowDone(long rowCount) {
        if ((rowCount & CHECK_MASK) == 0L) {
            report(rowCount);
        }
    }

    private void report(long rowCount) {
        long now = System.nanoTime();
        long elapsedNanos = now - startedNanos;
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);
        long rowsPerSecond = elapsedSeconds == 0 ? rowCount : rowCount / elapsedSeconds;

        if (now >= nextListenNanos) {
            nextListenNanos = now + LISTEN_INTERVAL_NANOS;
            listener.rowsProfiled(rowCount, elapsedSeconds, rowsPerSecond);
        }

        if (now >= nextLogNanos) {
            nextLogNanos = now + LOG_INTERVAL_NANOS;
            Log.infof("IMPORT [%s] %s%d rows profiled after %d s (%d rows/s%s)",
                    phase, source == null || source.isBlank() ? "" : source + " - ",
                    rowCount, elapsedSeconds, rowsPerSecond, detail);
        }
    }
}
