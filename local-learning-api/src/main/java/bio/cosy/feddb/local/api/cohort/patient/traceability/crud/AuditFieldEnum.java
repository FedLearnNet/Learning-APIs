package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

public enum AuditFieldEnum {
    // Fields that can be searched and sorted
    EXTERNAL_PATIENT_ID("externalPatientId", true, true),
    KEYCLOAK_ID("keycloakId", true, true),
    CONNECTOR_ID("connectorId", true, true),
    RUN_ID("runId", true, true),
    REVISION_TYPE("revisionType", true, true),
    COHORT_ID("cohort.id", true, false), // Internal field for cohort filtering - not user-sortable

    // Fields that can only be sorted (not searched)
    REVISION_NUMBER("revisionNumber", false, true),
    REVISION_TIMESTAMP("timestamp", false, true);

    private final String fieldName;
    private final boolean searchable;
    private final boolean sortable;

    AuditFieldEnum(String fieldName, boolean searchable, boolean sortable) {
        this.fieldName = fieldName;
        this.searchable = searchable;
        this.sortable = sortable;
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public boolean isSortable() {
        return sortable;
    }
}
