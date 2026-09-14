package bio.cosy.feddb.local.api.cohort.patient;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for reducing time-series data to single values
 * <p>
 * Ordering Strategy:
 * 1. Primary: visitTimestamp (if not null)
 * 2. If multiple entries have the same visitTimestamp, one is selected randomly (first found)
 * <p>
 * This handles cases where:
 * - Multiple visitIds exist without temporal ordering
 * - Entries were added at the same time (bulk imports)
 * <p>
 * Failure Handling:
 * - If a reduction method fails, it returns Optional.empty()
 * - The calling code (BO layer) is responsible for fallback logic
 */
//TODO Use Java map reduce features from java itself instead of stream operations
@ApplicationScoped
public class TimeSeriesReducer {

    @Inject
    PatientMapper patientMapper;

    /**
     * Reduces a list of numeric data entries to a single entry based on the reduction method
     *
     * @param entries List of data entries for the same schema node and patient (assumed to be numeric)
     * @param method  The reduction method to apply
     * @return The reduced data entry as DTO, or empty if no valid reduction possible
     */
    public Optional<PatientDataEntryDTO> reduceNumeric(List<PatientDataEntryEntity> entries,
                                                       TimeSeriesReductionMethod method) {
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        switch (method) {
            case LATEST:
                return getLatestEntry(entries);
            case EARLIEST:
                return getEarliestEntry(entries);
            case AVERAGE:
                return getAverageEntry(entries);
            case MEDIAN:
                return getMedianEntry(entries);
            case MAX:
                return getMaxEntry(entries);
            case MIN:
                return getMinEntry(entries);
            case COUNT:
                return getCountEntry(entries);
            default:
                return getLatestEntry(entries);
        }
    }

    /**
     * Reduces a list of non-numeric data entries to a single entry based on the reduction method
     *
     * @param entries List of data entries for the same schema node and patient (assumed to be non-numeric)
     * @param method  The reduction method to apply (must be applicable to non-numeric data)
     * @return The reduced data entry as DTO, or empty if no valid reduction possible
     */
    public Optional<PatientDataEntryDTO> reduceNonNumeric(List<PatientDataEntryEntity> entries,
                                                          TimeSeriesReductionMethod method) {
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        // Validate that method is applicable to non-numeric data
        if (method.isNumericOnly()) {
            throw new IllegalArgumentException("Reduction method '" + method.getValue() +
                    "' is not applicable to non-numeric data");
        }

        switch (method) {
            case LATEST:
                return getLatestEntry(entries);
            case EARLIEST:
                return getEarliestEntry(entries);
            case COUNT:
                return getCountEntry(entries);
            default:
                return getLatestEntry(entries);
        }
    }

    private Optional<PatientDataEntryDTO> getLatestEntry(List<PatientDataEntryEntity> entries) {
        return entries.stream()
                .max((e1, e2) -> compareByTimestamp(e1, e2))
                .map(patientMapper::entityToDto);
    }

    private Optional<PatientDataEntryDTO> getEarliestEntry(List<PatientDataEntryEntity> entries) {
        return entries.stream()
                .min((e1, e2) -> compareByTimestamp(e1, e2))
                .map(patientMapper::entityToDto);
    }

    /**
     * Compares two entries by timestamp with fallback logic:
     * 1. Primary: visitTimestamp (if not null)
     * 2. If both visitTimestamp are equal, comparison returns 0 (random selection by stream operations)
     *
     * @param e1 first entry
     * @param e2 second entry
     * @return comparison result (-1, 0, 1)
     */
    private static int compareByTimestamp(PatientDataEntryEntity e1, PatientDataEntryEntity e2) {
        // Primary ordering: visitTimestamp
        Instant ts1 = e1.getVisitTimestamp();
        Instant ts2 = e2.getVisitTimestamp();

        if (ts1 != null && ts2 != null) {
            return ts1.compareTo(ts2);
        } else if (ts1 != null) {
            return 1; // e1 is newer (has timestamp)
        } else if (ts2 != null) {
            return -1; // e2 is newer (has timestamp)
        }

        // Both timestamps are null - return 0 for random selection
        // This handles the case where multiple visitIds were added at the same time
        return 0;
    }


    /**
     * Determines if a data entry contains numeric data by checking the schema node's data type
     *
     * @param entry the data entry to check
     * @return true if the entry's schema node has a numeric data type (INT or FLOAT)
     * @throws IllegalStateException if the schema node or data type is null (indicates serious internal state issue)
     */
    public static boolean isNumericData(PatientDataEntryEntity entry) {
        if (entry.getSchemaNode() == null) {
            throw new IllegalStateException("PatientDataEntryEntity has null schema node - this indicates a serious internal state issue");
        }

        if (entry.getSchemaNode().getDataType() == null) {
            throw new IllegalStateException("SchemaNode has null data type - this indicates a serious internal state issue");
        }

        if (entry.getSchemaNode().getDataType().getType() == null) {
            throw new IllegalStateException("DataType has null type - this indicates a serious internal state issue");
        }

        DataTypes dataType = entry.getSchemaNode().getDataType().getType();
        return dataType == DataTypes.INT || dataType == DataTypes.FLOAT;
    }

    /**
     * Determines if a list of entries contains only numeric data
     *
     * @param entries the entries to check
     * @return true if all entries contain numeric data
     */
    public static boolean isAllNumericData(List<PatientDataEntryEntity> entries) {
        return entries.stream().allMatch(TimeSeriesReducer::isNumericData);
    }

    private Optional<PatientDataEntryDTO> getAverageEntry(List<PatientDataEntryEntity> entries) {
        // For numeric types, calculate average and create a synthetic DTO
        // Return empty if calculation fails (BO will handle fallback)
        try {
            if (isAllNumericData(entries)) {
                return calculateNumericAverage(entries);
            }
        } catch (Exception e) {
            // Return empty to let BO handle fallback
        }
        return Optional.empty();
    }

    private Optional<PatientDataEntryDTO> getMedianEntry(List<PatientDataEntryEntity> entries) {
        // Calculate median, return empty if it fails (BO will handle fallback)
        try {
            if (isAllNumericData(entries)) {
                return calculateNumericMedian(entries);
            }
        } catch (Exception e) {
            // Return empty to let BO handle fallback
        }
        return Optional.empty();
    }

    private Optional<PatientDataEntryDTO> getMaxEntry(List<PatientDataEntryEntity> entries) {
        // For numeric types, find max value
        // Return empty if max calculation fails (BO will handle fallback)
        try {
            if (isAllNumericData(entries)) {
                return findNumericMax(entries);
            }
        } catch (Exception e) {
            // Return empty to let BO handle fallback
        }
        return Optional.empty();
    }

    private Optional<PatientDataEntryDTO> getMinEntry(List<PatientDataEntryEntity> entries) {
        // For numeric types, find min value
        // Return empty if min calculation fails (BO will handle fallback)
        try {
            if (isAllNumericData(entries)) {
                return findNumericMin(entries);
            }
        } catch (Exception e) {
            // Return empty to let BO handle fallback
        }
        return Optional.empty();
    }

    private static Optional<PatientDataEntryDTO> getCountEntry(List<PatientDataEntryEntity> entries) {
        // Create a synthetic DTO with count as integer value
        // Return empty if count creation fails (BO will handle fallback)
        try {
            PatientDataEntryDTO countDto = new PatientDataEntryDTO();
            PatientDataEntryEntity template = entries.get(0);

            // Copy basic properties from template
            countDto.setSchemaNodeId(template.getSchemaNode().getId());
            countDto.setVisitTimestamp(Instant.now().toString()); // Use current timestamp for count

            // Set count as integer value
            countDto.setValue(entries.size());

            return Optional.of(countDto);
        } catch (Exception e) {
            // Return empty to let BO handle fallback
            return Optional.empty();
        }
    }

    private Optional<PatientDataEntryDTO> calculateNumericAverage(List<PatientDataEntryEntity> entries) {
        try {
            // If there's only one entry, just return that entry directly
            if (entries.size() == 1) {
                return Optional.of(patientMapper.entityToDto(entries.get(0)));
            }

            // For multiple entries, calculate the average
            PatientDataEntryDTO avgDto = new PatientDataEntryDTO();
            PatientDataEntryEntity template = entries.get(0);

            // Copy non-value properties from template
            avgDto.setSchemaNodeId(template.getSchemaNode().getId());

            // For multiple entries, leave timestamps null since it's a calculated average

            // Calculate average using schema node-aware numeric extraction
            double sum = entries.stream()
                    .mapToDouble(TimeSeriesReducer::getNumericValue)
                    .sum();

            double average = sum / entries.size();

            // For average, always return as double to preserve precision (like 30.5)
            avgDto.setValue(average);

            return Optional.of(avgDto);
        } catch (Exception e) {
            // Return empty to trigger fallback to latest
            return Optional.empty();
        }
    }

    private Optional<PatientDataEntryDTO> calculateNumericMedian(List<PatientDataEntryEntity> entries) {
        try {
            // Sort entries by their numeric values
            List<PatientDataEntryEntity> sortedEntries = entries.stream()
                    .sorted((e1, e2) -> {
                        double val1 = getNumericValue(e1);
                        double val2 = getNumericValue(e2);
                        return Double.compare(val1, val2);
                    })
                    .collect(java.util.stream.Collectors.toList());

            PatientDataEntryEntity medianEntity;
            int size = sortedEntries.size();

            if (size % 2 == 0) {
                // Even number of elements - return the higher of the two middle elements
                // This ensures we return an actual data entry, not a calculated average
                // THIS IS NIKS FAULT
                // HE INSISTED on taking the lower of the two middle elements
                // I still love him though
                medianEntity = sortedEntries.get((size / 2) - 1);
            } else {
                // Odd number of elements - return the middle element
                medianEntity = sortedEntries.get(size / 2);
            }

            // Convert the actual median entry to DTO
            return Optional.of(patientMapper.entityToDto(medianEntity));
        } catch (Exception e) {
            // Return empty to trigger fallback to latest
            return Optional.empty();
        }
    }

    private Optional<PatientDataEntryDTO> findNumericMax(List<PatientDataEntryEntity> entries) {
        return entries.stream()
                .max((e1, e2) -> {
                    double val1 = getNumericValue(e1);
                    double val2 = getNumericValue(e2);
                    return Double.compare(val1, val2);
                })
                .map(patientMapper::entityToDto);
    }

    private Optional<PatientDataEntryDTO> findNumericMin(List<PatientDataEntryEntity> entries) {
        return entries.stream()
                .min((e1, e2) -> {
                    double val1 = getNumericValue(e1);
                    double val2 = getNumericValue(e2);
                    return Double.compare(val1, val2);
                })
                .map(patientMapper::entityToDto);
    }

    private static double getNumericValue(PatientDataEntryEntity entry) {
        if (entry.getSchemaNode() == null) {
            throw new IllegalStateException("PatientDataEntryEntity has null schema node - this indicates a serious internal state issue");
        }

        if (entry.getSchemaNode().getDataType() == null) {
            throw new IllegalStateException("SchemaNode has null data type - this indicates a serious internal state issue");
        }

        if (entry.getSchemaNode().getDataType().getType() == null) {
            throw new IllegalStateException("DataType has null type - this indicates a serious internal state issue");
        }

        DataTypes dataType = entry.getSchemaNode().getDataType().getType();

        switch (dataType) {
            case INT:
                return entry.getValueInt() != null ? entry.getValueInt().doubleValue() : 0.0;
            case FLOAT:
                return entry.getValueFloat() != null ? entry.getValueFloat().doubleValue() : 0.0;
            default:
                throw new IllegalStateException("Attempting to get numeric value from non-numeric data type: " + dataType);
        }
    }
}
