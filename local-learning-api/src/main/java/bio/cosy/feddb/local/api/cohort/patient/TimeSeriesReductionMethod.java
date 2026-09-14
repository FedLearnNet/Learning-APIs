package bio.cosy.feddb.local.api.cohort.patient;

/**
 * Defines methods for reducing time-series data to single values per schema node
 */
public enum TimeSeriesReductionMethod {
    /**
     * Uses the latest (most recent) value based on timestampToUse
     */
    LATEST("latest"),

    /**
     * Uses the earliest (oldest) value based on timestampToUse
     */
    EARLIEST("earliest"),

    /**
     * Calculates the average of all numeric values (only applicable to numeric data types)
     */
    AVERAGE("average"),

    /**
     * Calculates the median of all numeric values (only applicable to numeric data types)
     */
    MEDIAN("median"),

    /**
     * Uses the maximum value for numeric data types, latest for others
     */
    MAX("max"),

    /**
     * Uses the minimum value for numeric data types, earliest for others
     */
    MIN("min"),

    /**
     * Counts the number of non-null entries per schema node
     */
    COUNT("count");

    private final String value;

    TimeSeriesReductionMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse reduction method from string value for numeric data
     * @param value the string value
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the value is not recognized or not applicable to numeric data
     */
    public static TimeSeriesReductionMethod fromValueForNumeric(String value) {
        TimeSeriesReductionMethod method = fromValue(value);
        // All methods are valid for numeric data
        return method;
    }

    /**
     * Parse reduction method from string value for non-numeric data
     * @param value the string value
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the value is not recognized or not applicable to non-numeric data
     */
    public static TimeSeriesReductionMethod fromValueForNonNumeric(String value) {
        TimeSeriesReductionMethod method = fromValue(value);
        if (method.isNumericOnly()) {
            throw new IllegalArgumentException("Reduction method '" + value +
                "' is not applicable to non-numeric data. Valid values for non-numeric data are: latest, earliest, count");
        }
        return method;
    }

    /**
     * Parse reduction method from string value
     * @param value the string value
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static TimeSeriesReductionMethod fromValue(String value) {
        for (TimeSeriesReductionMethod method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown reduction method: " + value +
            ". Valid values are: latest, earliest, average, median, max, min, count");
    }

    /**
     * Check if this reduction method is applicable to numeric data types only
     */
    public boolean isNumericOnly() {
        return this == AVERAGE || this == MEDIAN || this == MAX || this == MIN;
    }
}
