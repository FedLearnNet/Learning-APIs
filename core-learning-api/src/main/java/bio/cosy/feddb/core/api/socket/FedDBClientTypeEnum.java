package bio.cosy.feddb.core.api.socket;

public enum FedDBClientTypeEnum {
    EXISTING_QUERY,
    LEARNING_QUERY,
    DATA_STATISTICS,
    START_LEARNING,
    UPDATE_LEARNING,
    STOP_LEARNING,
    CURRENT_LEARNINGS,
    RUN_METRICS,
    RELAY_CERT,
    ERROR,
    NO_RESPONSE,
}
