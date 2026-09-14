package bio.cosy.feddb.local.api.importer.files.progress;

public enum ImportTableState {
    /** Known to be in the file, not yet started. */
    PENDING,
    READING,
    READ,
    /** Read, but holding nothing that could be used as a table. */
    SKIPPED,
    FAILED
}
