package bio.cosy.feddb.local.api.importer.run;

public enum ConnectorRunStep {
    INIT,
    EXTRACTING,
    TRANSFORMING,
    MAPPING,
    LOADING,
    FINISHED
}
