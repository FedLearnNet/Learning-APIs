package bio.cosy.feddb.core.api.pipeline;

public enum PipelineRunMode {
    // builds, pushes and publishes the tool version afterwards
    ALL,
    // builds and pushes, replaces the image of the current version
    BUILD_ONLY,
    LOCAL_BUILD,
    SCAN_ONLY
}
