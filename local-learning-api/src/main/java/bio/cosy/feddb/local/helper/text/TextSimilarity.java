package bio.cosy.feddb.local.helper.text;

import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.Set;

public class TextSimilarity {

    // weighted combination of LevenshteinDistance and jaccard
    public static double nameSimilarity(String column, String node) {
        String a = ColumnNames.toSearchText(column, true);
        String b = ColumnNames.toSearchText(node, true);

        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }

        String compactA = StringUtils.deleteWhitespace(a);
        String compactB = StringUtils.deleteWhitespace(b);

        if (compactA.equals(compactB)) {
            return 1;
        }

        return 0.95 * Math.max(
                editSimilarity(compactA, compactB),
                tokenSimilarity(a, b)
        );
    }

    //just Levenshtein
    public static double editSimilarity(String a, String b) {
        return 1 - (double) LevenshteinDistance.getDefaultInstance()
                .apply(a, b) / Math.max(a.length(), b.length());
    }

    // just jaccard
    public static double tokenSimilarity(String a, String b) {
        Set<String> tokensA = Set.of(a.split(" "));
        Set<String> tokensB = Set.of(b.split(" "));

        long shared = tokensA.stream()
                .filter(tokensB::contains)
                .count();

        return (double) shared / (tokensA.size() + tokensB.size() - shared);
    }

}
