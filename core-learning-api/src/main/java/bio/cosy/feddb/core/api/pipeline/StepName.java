package bio.cosy.feddb.core.api.pipeline;

public enum StepName {
    PREFLIGHT,
    CLONE_REPO,
    BUILD_IMAGE,
    FETCH_CONFIG,
    FETCH_FILES,
    SAST,
    PULL_IMAGE,
    PUSH_IMAGE,
    SCAN_IMAGE,
    MALWARE_CHECK,
    RUN_PYTEST,
    COLLECT_SUMMARY
}
