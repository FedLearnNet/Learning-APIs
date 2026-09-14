package bio.cosy.feddb.local.api.helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Splits id collections so that queries using them in an {@code IN} clause stay below the
 * PostgreSQL limit of 65535 bind parameters. Hibernate pads {@code IN} lists up to the next power
 * of two, so the chunk size is itself a power of two, which makes that padding a no-op.
 */
public final class IdChunks {

    public static final int MAX_IN_CLAUSE_IDS = 8192;

    private IdChunks() {
    }

    public static <I> void forEach(Collection<I> ids, Consumer<List<I>> action) {
        forEach(ids, MAX_IN_CLAUSE_IDS, action);
    }

    public static <I> void forEach(Collection<I> ids, int chunkSize, Consumer<List<I>> action) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<I> all = ids instanceof List<I> list ? list : new ArrayList<>(ids);
        for (int start = 0; start < all.size(); start += chunkSize) {
            action.accept(all.subList(start, Math.min(all.size(), start + chunkSize)));
        }
    }

    public static <I, T> List<T> collect(Collection<I> ids, Function<List<I>, List<T>> loader) {
        return collect(ids, MAX_IN_CLAUSE_IDS, loader);
    }

    public static <I, T> List<T> collect(Collection<I> ids, int chunkSize, Function<List<I>, List<T>> loader) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<T> found = new ArrayList<>();
        forEach(ids, chunkSize, chunk -> found.addAll(loader.apply(chunk)));
        return found;
    }
}
