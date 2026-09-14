package bio.cosy.feddb.core.api.datamodler.validation;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class BaseValidationBO {
    /**
     * Normalizes and validates a value against the provided data type.
     * If the value is invalid, returns false instead of throwing an exception.
     *
     * @param value
     * @param dataType
     * @return boolean indicating whether the value is valid for the given data type.
     */
    public boolean normalizeValidateValueSilent(Object value, DataTypeNodeDTO dataType) {
        try {
            normalizeValidateValue(value, dataType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Normalizes a value to the correct type based on the provided data type definition.
     * WARNING: If the input is null this just returns null as in theory null values might be valid
     * Be careful what you do with the output!
     *
     * @param value
     * @param dataType
     * @return
     */
    public Object normalizeValue(Object value, DataTypeNodeDTO dataType) {
        // The object can be any Object and should be the object corresponding to the DataType
        // We solve this by a dumb but works approach
        // make object string
        // make object correct type
        // return normalized object as object
        // next function should then cast correctly based on data type
        if (valueIsEmpty(value)) {
            return null;
        }
        switch (dataType.getType()) {
            case STRING:
            case CATEGORICAL:
                return value.toString();
            case INT:
                Double normalizedValueDouble;
                if (value instanceof Number) {
                    normalizedValueDouble = ((Number) value).doubleValue();
                } else {
                    try {
                        normalizedValueDouble = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Value " + value + " cannot be normalized to INT for data type " + dataType.getName());
                    }
                }
                // If the double value has any decimal part, we throw an error
                // otherwise normalization would result in loss of information
                if (normalizedValueDouble % 1 != 0) {
                    throw new IllegalArgumentException("Value " + value + " cannot be normalized to INT without loss of information for data type " + dataType.getName());
                }
                // Check if the value is within the valid range for Long to prevent overflow
                if (normalizedValueDouble < Long.MIN_VALUE || normalizedValueDouble > Long.MAX_VALUE) {
                    throw new IllegalArgumentException("Value " + value + " is out of range for INT (Long) data type " + dataType.getName());
                }
                return normalizedValueDouble.longValue();
            case FLOAT:
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                } else {
                    return Float.parseFloat(value.toString());
                }
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                } else {
                    // Parse from string representation
                    String strValue = value.toString().trim().toLowerCase();
                    if ("true".equals(strValue)) {
                        return true;
                    } else if ("false".equals(strValue)) {
                        return false;
                    } else {
                        throw new IllegalArgumentException("Value " + value + " cannot be normalized to BOOLEAN for data type " + dataType.getName());
                    }
                }
            case DATE:
                Instant instant = convertToInstant(value, dataType.getType());
                return instant.atZone(ZoneOffset.UTC).toLocalDate();
            case DATE_TIME:
                return convertToInstant(value, dataType.getType());
            case FILE:
                // FILE type is not yet implemented
                // TODO: implement FILE type normalization and validation
                throw new NotImplementedException("FILE data type validation/normalization is not yet implemented");
            default:
                throw new IllegalArgumentException("Unsupported data type: " + dataType.getType());
        }
    }

    public void normalizeValidateValue(Object value, DataTypeNodeDTO dataType) throws IllegalArgumentException {
        Object normalizedValue = normalizeValue(value, dataType);
        validateValue(normalizedValue, dataType);
    }

    public boolean isRequired(DataTypeNodeDTO dataType) {
        return dataType.getIsRequired() != null && dataType.getIsRequired();
    }

    public boolean valueIsEmpty(Object value) {
        if (value == null) {
            return true;
        }
        return StringUtils.isEmpty(value.toString());
    }

    public void validateValue(Object value, DataTypeNodeDTO dataType) throws IllegalArgumentException {
        // Allowed values validation (only for categorical dataTypes and only if the value is given)
        if (!valueIsEmpty(value) && dataType.getType() == DataTypes.CATEGORICAL && dataType.getOptions() != null && !dataType.getOptions().isEmpty()) {
            if (!dataType.getOptions().contains((String) value)) {
                throw new IllegalArgumentException("Value " + value + " is not allowed for data type " + dataType.getName());
            }
        }
        // validations themselves

        // No validations to do
        if (dataType.getValidations() == null || dataType.getValidations().isEmpty()) {
            return;
        }
        boolean isReq = isRequired(dataType);
        // For null values only the required validation makes sense
        if (valueIsEmpty(value) && isReq) {
            throw new IllegalArgumentException("Value is required for data type " + dataType.getName());
        }

        if (valueIsEmpty(value) && dataType.getAllowNullValues()) {
            return;
        }

        // For non null values we validate all validations (except required which is already handled)
        for (DataTypeValidationDTO validation : dataType.getValidations()) {
            switch (validation.getName()) {
                case MINLENGTH:
                    if (value instanceof String) {
                        Long minLength = Long.parseLong(validation.getValidator());
                        if (((String) value).length() < minLength) {
                            throw new IllegalArgumentException("Value must be at least " + minLength + " characters long for data type " + dataType.getName());
                        }
                    } else if (value != null) {
                        throw new IllegalArgumentException("Min length validation can only be applied to string values for data type " + dataType.getName());
                    }
                    break;
                case MAXLENGTH:
                    if (value instanceof String) {
                        Long maxLength = Long.parseLong(validation.getValidator());
                        if (((String) value).length() > maxLength) {
                            throw new IllegalArgumentException("Value must be at most " + maxLength + " characters long for data type " + dataType.getName());
                        }
                    } else if (value != null) {
                        throw new IllegalArgumentException("Max length validation can only be applied to string values for data type " + dataType.getName());
                    }
                    break;
                case PATTERN:
                    if (value instanceof String) {
                        String pattern = validation.getValidator();
                        if (!((String) value).matches(pattern)) {
                            throw new IllegalArgumentException("Value does not match required pattern for data type " + dataType.getName());
                        }
                    } else if (value != null) {
                        throw new IllegalArgumentException("Pattern validation can only be applied to string values for data type " + dataType.getName());
                    }
                    break;
                case MIN:
                    // Numerical validation
                    if (dataType.getType() == DataTypes.FLOAT || dataType.getType() == DataTypes.INT) {
                        if (value instanceof Number) {
                            double minValue = Double.parseDouble(validation.getValidator());
                            if (((Number) value).doubleValue() < minValue) {
                                throw new IllegalArgumentException("Value must be at least " + minValue + " for data type " + dataType.getName());
                            }
                        } else if (value != null) {
                            throw new IllegalArgumentException("Min validation can only be applied to numerical values for data type " + dataType.getName());
                        }
                        // Date/time validation
                    } else if (dataType.getType() == DataTypes.DATE || dataType.getType() == DataTypes.DATE_TIME) {
                        Instant minInstant = convertToInstant(validation.getValidator(), dataType.getType());
                        Instant valueInstant = convertToInstant(value, dataType.getType());
                        if (valueInstant.isBefore(minInstant)) {
                            throw new IllegalArgumentException("Value must be at least " + minInstant + " for data type " + dataType.getName());
                        }
                        // Should never happen as this is a wrong validation object
                    } else if (value != null) {
                        throw new IllegalArgumentException("Min validation can only be applied to numerical or date/time values for data type " + dataType.getName());
                    }
                    break;
                case MAX:
                    // Numerical validation
                    if (dataType.getType() == DataTypes.FLOAT || dataType.getType() == DataTypes.INT) {
                        if (value instanceof Number) {
                            double maxValue = Double.parseDouble(validation.getValidator());
                            if (((Number) value).doubleValue() > maxValue) {
                                throw new IllegalArgumentException("Value must be at most " + maxValue + " for data type " + dataType.getName());
                            }
                        } else if (value != null) {
                            throw new IllegalArgumentException("Max validation can only be applied to numerical values for data type " + dataType.getName());
                        }
                        // Date/time validation
                    } else if (dataType.getType() == DataTypes.DATE || dataType.getType() == DataTypes.DATE_TIME) {
                        Instant maxInstant = convertToInstant(validation.getValidator(), dataType.getType());
                        Instant valueInstant = convertToInstant(value, dataType.getType());
                        if (valueInstant.isAfter(maxInstant)) {
                            throw new IllegalArgumentException("Value must be at most " + maxInstant + " for data type " + dataType.getName());
                        }
                        // Should never happen as this is a wrong validation object
                    } else if (value != null) {
                        throw new IllegalArgumentException("Max validation can only be applied to numerical or date/time values for data type " + dataType.getName());
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown validation type: " + validation.getName());
            }
        }
    }

    public void validateDataTypeValidations(Set<DataTypeValidationDTO> validations, DataTypes dataType) {
        if (validations == null || validations.isEmpty()) {
            return; // No validations to validate
        }

        if (dataType == null) {
            throw new IllegalArgumentException("Data type must not be null when validating validations");
        }

        for (DataTypeValidationDTO validation : validations) {
            if (validation.getName() == null) {
                throw new IllegalArgumentException("Validation name must not be null");
            }

            validateValidationForDataType(validation, dataType);
        }
    }

    public void validateValidationForDataType(DataTypeValidationDTO validation, DataTypes dataType) {
        if (validation.getValidator() == null) {
            throw new IllegalArgumentException("Validator value must not be null for validation type " + validation.getName());
        }

        switch (validation.getName()) {
            case MINLENGTH:
            case MAXLENGTH:
                // Length validations only make sense for STRING data types
                if (dataType != DataTypes.STRING) {
                    throw new IllegalArgumentException("MINLENGTH/MAXLENGTH validations can only be applied to STRING data types, but got: " + dataType);
                }
                // Validate that the validator is a valid positive integer
                try {
                    Long length = Long.parseLong(validation.getValidator());
                    if (length < 0) {
                        throw new IllegalArgumentException("Length validation must be a non-negative number, but got: " + validation.getValidator());
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Length validation must be a valid number, but got: " + validation.getValidator());
                }
                break;

            case PATTERN:
                // Pattern validation only makes sense for STRING data types
                if (dataType != DataTypes.STRING) {
                    throw new IllegalArgumentException("PATTERN validation can only be applied to STRING data types, but got: " + dataType);
                }
                // Validate that the validator is a valid regex pattern
                try {
                    Pattern.compile(validation.getValidator());
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("PATTERN validation must be a valid regular expression, but got: " + validation.getValidator() + " - " + e.getMessage());
                }
                break;

            case MIN:
            case MAX:
                // Min/Max validations only make sense for numerical data types (including dates)
                if (dataType != DataTypes.INT && dataType != DataTypes.FLOAT && dataType != DataTypes.DATE && dataType != DataTypes.DATE_TIME) {
                    throw new IllegalArgumentException("MIN/MAX validations can only be applied to INT, FLOAT, DATE or DATE_TIME data types, but got: " + dataType);
                }
                // Validate that the validator is a valid number or date/time string
                try {
                    if (dataType == DataTypes.DATE || dataType == DataTypes.DATE_TIME) {
                        convertToInstant(validation.getValidator(), dataType);
                    } else {
                        Double.parseDouble(validation.getValidator());
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("MIN/MAX validation must be a valid number, but got: " + validation.getValidator());
                } catch (IllegalArgumentException e) {
                    // The exception thrown by convertToInstant is caught here
                    throw new IllegalArgumentException("MIN/MAX validation must be a valid ISO datetime/date string or epoch (seconds/milliseconds), but got: " + validation.getValidator() + " - " + e.getMessage());
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown validation type: " + validation.getName());
        }
    }

    /**
     * Convert a value to an Instant in UTC timezone.
     * If the value has a higher precision than the dataType supports (datetime on a date)
     * An illegalArgumentException is thrown as this would lead to loss of information which we want to avoid
     * <p>
     * Timezone Handling Rules:
     * - Timezone-AWARE inputs: Converted to their UTC equivalent (e.g., "2024-01-01T12:00:00+02:00" → UTC)
     * - Timezone-NAIVE inputs: Assumed to already be in UTC (e.g., "2024-01-01T12:00:00" → treated as UTC)
     * <p>
     * Supported Input Types:
     * - ISO 8601 strings: with timezone ("2024-01-01T12:00:00+02:00", "2024-01-01T12:00:00Z"),
     * without timezone ("2024-01-01T12:00:00", "2024-01-01")
     * - Epoch timestamps: seconds or milliseconds (as string or long number)
     * - Java time objects: LocalDate, LocalDateTime, ZonedDateTime, Instant
     */
    public Instant convertToInstant(Object value, DataTypes dataType) {
        if (valueIsEmpty(value)) {
            // null check must be handled by the caller
            return null;
        }
        // String preprocessing
        // Overwrite value with the correctly parsed value, not applying logic yet
        if (value instanceof String) {
            // String representation of date/datetime
            // We go through from the most precise to the least precise formatter
            // Epochs > ZonedDateTime -> LocalDateTime -> LocalDate
            String valueString = value.toString().trim();

            // Epoch values
            if (valueString.matches("^-?\\d+$")) {
                try {
                    return instantFromEpoch(Long.parseLong(valueString));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Cannot parse numeric string value to Instant: " + valueString);
                }
            }
            // ZonedDateTime, LocalDateTime, LocalDate
            try {
                // ZonedDateTime: Expect an iso datetime WITH any correctly formatted timezone
                value = ZonedDateTime.parse(valueString);
            } catch (DateTimeParseException errZonedDateTimeParser) {
                // Note: we don't try Instant.parse as this according to the documentation
                // also uses the ISO_ZONED_DATE_TIME formatter, just as ZonedDateTime.parse
                try {
                    // LocalDateTime: Iso datetime WITHOUT timezone
                    value = LocalDateTime.parse(valueString);
                } catch (DateTimeParseException errLocalDateTimeParser) {
                    try {
                        // LocalDate: ISO date without time (nor timezone)
                        value = LocalDate.parse(valueString);
                    } catch (DateTimeParseException errLocalDateParser) {
                        throw new IllegalArgumentException("Cannot parse string value to Instant using any supported format: " + valueString + " - " + errLocalDateParser.getMessage());
                    }
                }
            }
        }

        // Convert to instant
        Instant finalTimeInstant;
        if (value instanceof Number) {
            finalTimeInstant = instantFromEpoch(((Number) value).longValue());
        } else if (value instanceof ZonedDateTime) {
            // ZonedDateTime contains timezone info, convert to UTC Instant
            finalTimeInstant = ((ZonedDateTime) value).toInstant();
        } else if (value instanceof Instant) {
            // Instant is always returned as-is (already in UTC)
            finalTimeInstant = (Instant) value;
        } else if (value instanceof LocalDateTime) {
            // LocalDateTime has no timezone info, assume UTC
            finalTimeInstant = ((LocalDateTime) value).atZone(ZoneOffset.UTC).toInstant();
        } else if (value instanceof LocalDate) {
            // LocalDate has no timezone info, assume UTC at start of day
            finalTimeInstant = ((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } else {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Instant");
        }

        // For the date data type we check if the final instant
        // has time information that would be lost when converting to date
        if (dataType == DataTypes.DATE) {
            LocalDate date = finalTimeInstant.atZone(ZoneOffset.UTC).toLocalDate();
            Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            if (!finalTimeInstant.equals(startOfDay)) {
                throw new IllegalArgumentException("Value has time information that would be lost when converting to DATE data type: " + finalTimeInstant);
            }
        }

        return finalTimeInstant;
    }

    private Instant instantFromEpoch(long epochValue) {
        // Heuristic: Distinguish between epoch seconds and milliseconds
        //
        // Threshold: 10,000,000,000 seconds (~year 2286)
        // - Values <= threshold are treated as epoch SECONDS (up to ~300 years from 1970)
        // - Values > threshold are treated as epoch MILLISECONDS
        // Edge cases:
        // - Negative values: Treated as seconds (goes back before 1970)
        // - We interpret 10_000_000_001L as milliseconds, so basically 1970-01-01 plus 1 millisecond
        //   If the user intented the year 2286 then we interpret it wrong
        //   It is a heuristic after all, hopefully this will just never happen
        //   If we wanna be safer we would need to
        //   - get user input if this is seconds or milliseconds
        //   - convert to milliseconds
        //   - only support milliseconds here
        final long SECONDS_THRESHOLD = 10_000_000_000L; // Approximately year 2286

        if (Math.abs(epochValue) <= SECONDS_THRESHOLD) {
            return Instant.ofEpochSecond(epochValue);
        }
        return Instant.ofEpochMilli(epochValue);
    }
}
