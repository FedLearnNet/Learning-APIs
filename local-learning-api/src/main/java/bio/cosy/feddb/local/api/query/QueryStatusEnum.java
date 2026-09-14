package bio.cosy.feddb.local.api.query;

public enum QueryStatusEnum {
    UNKNOWN,
    RECEIVED_EAM,
    REJECTED_EAM_PRE_HARMONIZED,
    RECEIVED_HARMONIZED,
    REJECTED_HARMONIZED,
    REJECTED_EAM_POST_HARMONIZED,
    COMPLETED
}
