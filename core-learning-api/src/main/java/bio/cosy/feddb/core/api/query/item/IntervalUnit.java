package bio.cosy.feddb.core.api.query.item;

import lombok.Getter;

@Getter
public enum IntervalUnit {
    MINUTES("minutes", 1L),
    HOURS("hours", 60L),
    DAYS("days", 24L * 60),
    WEEKS("weeks", 7L * 24 * 60),
    MONTHS("months", 31L * 24 * 60),
    YEARS("years", 366L * 24 * 60);

    private final String sqlUnit;
    // upper bound, used to cap intervals
    private final long maxMinutes;

    IntervalUnit(String sqlUnit, long maxMinutes) {
        this.sqlUnit = sqlUnit;
        this.maxMinutes = maxMinutes;
    }
}
