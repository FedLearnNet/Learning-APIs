package de.unihamburg.daibetes.api.build.pipeline.steps;

public enum StepName {
    PREFLIGHT,
    CLONE_REPO,
    BUILD_IMAGE,
    FETCH_CONFIG,
    FETCH_FILES,
    PUSH_IMAGE,
    SCAN_IMAGE,
    MALWARE_CHECK,
    RUN_PYTEST,
    COLLECT_SUMMARY
}
