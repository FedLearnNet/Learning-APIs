package bio.cosy.feddb.local.api.search;

import bio.cosy.feddb.core.base.BaseDTO;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class SearchScoreUtil {

    private SearchScoreUtil() {
    }

    public static int score(String query, String... values) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return 0;
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(SearchScoreUtil::normalize)
                .mapToInt(value -> scoreValue(normalizedQuery, value))
                .max()
                .orElse(0);
    }

    public static <T extends BaseDTO> SearchResultDTO<T> toResult(
            SearchResultType type,
            T result,
            String title,
            int score) {
        SearchResultDTO<T> dto = new SearchResultDTO<>();
        dto.setType(type);
        dto.setResult(result);
        dto.setTitle(title);
        dto.setScore(score);
        return dto;
    }

    private static int scoreValue(String query, String value) {
        if (value.isEmpty()) {
            return 0;
        }
        if (value.equals(query)) {
            return 100;
        }
        if (value.startsWith(query)) {
            return 80;
        }
        if (value.contains(query)) {
            return 60;
        }
        int wordMatches = (int) Arrays.stream(query.split("\\s+"))
                .filter(word -> !word.isBlank())
                .filter(value::contains)
                .count();
        return wordMatches > 0 ? 20 + wordMatches * 10 : 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
