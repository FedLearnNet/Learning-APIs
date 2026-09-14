package bio.cosy.feddb.core.api.file.analytics;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Profiles one table across several threads.
 *
 * <p>The work is split by <em>column</em>, not by row. Each column's aggregator therefore still sees
 * every value of its column, in row order, from a single thread - so the profile of a table does not
 * depend on how many cores happened to run it. Splitting by row would instead mean merging partial
 * sketches, whose distinct-count and quantile estimates shift with how the input was divided; a file
 * would then profile differently on different machines.</p>
 *
 * <p>This is what a single very large table needs. Reading a multi-table archive already uses every
 * core by reading its tables side by side, but a file whose bulk sits in one table - which is the
 * usual shape of an extract - gets nothing from that.</p>
 *
 * <p>Rows are handed over in batches, and the batch is complete before the next one is read, so the
 * shards never see a partly written row and no locking is needed on the row data itself.</p>
 */
public final class ShardedRowProfiler implements AutoCloseable {

    /**
     * Rows handed to the shards at a time. Large enough that the per-batch handover disappears next
     * to the profiling itself, small enough to stay well inside cache.
     */
    private static final int BATCH_ROWS = 8_192;

    /** Number of rendered rows kept as a sample of the input. */
    private static final int MAX_SAMPLES = 3;

    private final List<String> columns;

    /** Single-threaded fallback; when set, no shards, batch or executor exist at all. */
    private final RowProfiler sequential;

    private final List<Shard> shards;
    private final ExecutorService executor;
    private final RowProgress progress;

    private final Object[][] batch;
    private int batched;

    private final List<String> samples = new ArrayList<>(MAX_SAMPLES);
    private long rowCount;

    private ShardedRowProfiler(
            List<String> columns,
            String source,
            int workers,
            RowScanListener listener
    ) {
        this.columns = columns == null ? List.of() : List.copyOf(columns);

        int effectiveWorkers = Math.min(Math.max(1, workers), this.columns.size());
        if (effectiveWorkers <= 1) {
            this.sequential = RowProfiler.of(this.columns, source, listener);
            this.shards = List.of();
            this.executor = null;
            this.batch = null;
            this.progress = null;
            return;
        }

        this.sequential = null;
        this.shards = buildShards(this.columns, effectiveWorkers);
        this.executor = Executors.newFixedThreadPool(this.shards.size(), Thread.ofPlatform()
                .name("importer-profiler", 0)
                .daemon()
                .factory());
        this.batch = new Object[BATCH_ROWS][];
        this.progress = new RowProgress("Statistics", source,
                ", " + this.columns.size() + " columns, " + this.shards.size() + " shards", listener);
    }

    /**
     * @param workers upper bound on threads; the effective count is also capped by the column count,
     *                and one means no threads are started at all
     */
    public static ShardedRowProfiler of(
            List<String> columns,
            String source,
            int workers
    ) {
        return of(columns, source, workers, RowScanListener.NONE);
    }

    /**
     * @param listener told how the scan is coming along, for whoever is watching the import
     */
    public static ShardedRowProfiler of(
            List<String> columns,
            String source,
            int workers,
            RowScanListener listener
    ) {
        return new ShardedRowProfiler(columns, source, workers, listener);
    }

    /**
     * Assigns columns to shards round robin.
     *
     * <p>Column cost varies by an order of magnitude with the kind of data in it, and columns of a
     * kind tend to sit together in a table. Interleaving spreads that better than handing each shard
     * a contiguous block would.</p>
     */
    private static List<Shard> buildShards(List<String> columns, int workers) {
        List<List<Integer>> assignment = new ArrayList<>(workers);
        for (int worker = 0; worker < workers; worker++) {
            assignment.add(new ArrayList<>());
        }
        for (int index = 0; index < columns.size(); index++) {
            assignment.get(index % workers).add(index);
        }

        List<Shard> shards = new ArrayList<>(workers);
        for (List<Integer> indices : assignment) {
            int[] columnIndices = indices.stream().mapToInt(Integer::intValue).toArray();
            List<String> shardColumns = indices.stream().map(columns::get).toList();
            shards.add(new Shard(columnIndices, RowProfiler.shard(shardColumns)));
        }
        return shards;
    }

    /** Feeds one row of cells, in column order. */
    public void accept(Object[] values) {
        if (sequential != null) {
            sequential.accept(values);
            return;
        }

        if (samples.size() < MAX_SAMPLES) {
            samples.add(renderSample(values));
        }
        batch[batched++] = values;
        if (batched == BATCH_ROWS) {
            flush();
        }
        rowCount++;
        progress.rowDone(rowCount);
    }

    private void flush() {
        if (batched == 0) {
            return;
        }
        int size = batched;
        List<Callable<Void>> tasks = new ArrayList<>(shards.size());
        for (Shard shard : shards) {
            tasks.add(() -> {
                shard.consume(batch, size);
                return null;
            });
        }
        try {
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while profiling table columns", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
        }
        Arrays.fill(batch, 0, size, null);
        batched = 0;
    }

    private String renderSample(Object[] values) {
        LinkedHashMap<String, String> sample = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            Object value = values != null && index < values.length ? values[index] : null;
            sample.put(columns.get(index), value == null ? null : String.valueOf(value));
        }
        return sample.toString();
    }

    /** Builds the profiles, in the table's column order. The instance is finished afterwards. */
    public FileProfile finish() {
        if (sequential != null) {
            return sequential.finish();
        }

        flush();
        ColumnProfile[] ordered = new ColumnProfile[columns.size()];
        for (Shard shard : shards) {
            List<ColumnProfile> profiles = shard.profiler().profiles(rowCount);
            for (int position = 0; position < shard.columnIndices().length; position++) {
                ordered[shard.columnIndices()[position]] = profiles.get(position);
            }
        }
        close();
        return new FileProfile("", rowCount, List.of(ordered), List.copyOf(samples));
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** One thread's columns and the aggregators for them. */
    private record Shard(int[] columnIndices, RowProfiler profiler) {

        void consume(Object[][] rows, int size) {
            for (int row = 0; row < size; row++) {
                Object[] values = rows[row];
                profiler.startRow();
                for (int position = 0; position < columnIndices.length; position++) {
                    int column = columnIndices[position];
                    profiler.acceptObject(position,
                            values != null && column < values.length ? values[column] : null);
                }
                profiler.endRow();
            }
        }
    }
}
