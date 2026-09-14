package bio.cosy.feddb.core.api.file.analytics;

/**
 * Told how a row scan is coming along, at the same points the scan reports to the log.
 *
 * <p>Profiling is the longest phase of a large import - minutes on a table of millions of rows - and
 * the rows counted here are the only measure of it that exists while it runs. The log has always said
 * so; this lets the same thing be said to whoever is watching the import.</p>
 */
@FunctionalInterface
public interface RowScanListener {

    /** Reports nothing. */
    RowScanListener NONE = (rows, elapsedSeconds, rowsPerSecond) -> {
    };

    /**
     * @param rows           rows profiled so far
     * @param elapsedSeconds how long the scan has been running
     * @param rowsPerSecond  the rate over the whole scan so far
     */
    void rowsProfiled(long rows, long elapsedSeconds, long rowsPerSecond);
}
