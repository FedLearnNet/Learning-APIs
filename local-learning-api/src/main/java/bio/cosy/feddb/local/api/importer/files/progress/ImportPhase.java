package bio.cosy.feddb.local.api.importer.files.progress;

public enum ImportPhase {

    //just by browser, never here
    TRANSFER,

    PARSING,

    //Get data for preview
    SAMPLING,

    //Reupload Validation
    COMPARING,

    SUCCEEDED,

    // if connector reupload validation fails
    REFUSED,

    FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == REFUSED || this == FAILED;
    }
}
