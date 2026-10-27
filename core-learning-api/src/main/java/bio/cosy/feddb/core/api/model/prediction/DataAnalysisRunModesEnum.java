package bio.cosy.feddb.core.api.model.prediction;


public enum DataAnalysisRunModesEnum {
    PREDICTION,
    WORKFLOW;

    public static DataAnalysisRunModesEnum fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        if (PREDICTION.isEqual(value)) {
            return PREDICTION;
        } else if (WORKFLOW.isEqual(value)) {
            return WORKFLOW;
        } else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    public boolean isEqual(String value) {
        if (value == null) {
            return false;
        }
        return this.name().equalsIgnoreCase(value);
    }
}
