package bio.cosy.feddb.core.api.info;

// imports of ontologies, schemas, tools etc. on service startup, ordered by severity
public enum StartupImportState {
    READY,
    IMPORTING,
    FAILED;

    // the worse of both, unknown counts as READY
    public StartupImportState combine(StartupImportState other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
