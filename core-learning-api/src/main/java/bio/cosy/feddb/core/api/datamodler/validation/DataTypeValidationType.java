package bio.cosy.feddb.core.api.datamodler.validation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public enum DataTypeValidationType {
    MINLENGTH,
    MAXLENGTH,
    PATTERN,
    MIN,
    MAX
}
