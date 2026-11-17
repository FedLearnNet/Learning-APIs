package bio.cosy.feddb.local.api.importer.run.preview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Picks the preview rows that show the most data. Greedy column coverage first: each pick is the row
 * that fills the most columns no picked row has a value in yet (ties: more filled cells, then the
 * earlier row). Once no row adds a column, the remaining slots go to the rows with the most filled
 * cells. The picked rows are returned in their original order.
 */
public final class PreviewRowSelector {

    private PreviewRowSelector() {
    }

    public static List<Map<String, Object>> select(List<Map<String, Object>> pool, List<String> columns, int n) {
        if (pool == null) {
            return List.of();
        }
        if (n <= 0) {
            return List.of();
        }
        if (pool.size() <= n) {
            return pool;
        }

        List<String> cols = columns == null || columns.isEmpty() ? columnsOf(pool) : columns;
        boolean[][] filled = new boolean[pool.size()][cols.size()];
        int[] cells = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            Map<String, Object> row = pool.get(i);
            for (int c = 0; c < cols.size(); c++) {
                if (PreviewCells.hasValue(row, cols.get(c))) {
                    filled[i][c] = true;
                    cells[i]++;
                }
            }
        }

        TreeSet<Integer> chosen = new TreeSet<>();
        boolean[] covered = new boolean[cols.size()];
        while (chosen.size() < n) {
            int best = -1;
            int bestGain = 0;
            for (int i = 0; i < pool.size(); i++) {
                if (chosen.contains(i)) {
                    continue;
                }
                int gain = 0;
                for (int c = 0; c < cols.size(); c++) {
                    if (filled[i][c] && !covered[c]) {
                        gain++;
                    }
                }
                if (gain > bestGain || (gain == bestGain && gain > 0 && cells[i] > cells[best])) {
                    best = i;
                    bestGain = gain;
                }
            }
            if (best < 0) {
                break;
            }
            chosen.add(best);
            for (int c = 0; c < cols.size(); c++) {
                covered[c] |= filled[best][c];
            }
        }

        IntStream.range(0, pool.size())
                .filter(i -> !chosen.contains(i))
                .boxed()
                .sorted(Comparator.<Integer>comparingInt(i -> cells[i]).reversed()
                        .thenComparingInt(i -> i))
                .limit(n - chosen.size())
                .forEach(chosen::add);

        List<Map<String, Object>> selected = new ArrayList<>(chosen.size());
        chosen.forEach(i -> selected.add(pool.get(i)));
        return selected;
    }

    private static List<String> columnsOf(List<Map<String, Object>> rows) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row != null) {
                all.addAll(row.keySet());
            }
        });
        return new ArrayList<>(all);
    }
}
