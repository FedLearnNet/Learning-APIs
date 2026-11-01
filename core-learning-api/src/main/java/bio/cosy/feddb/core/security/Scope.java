package bio.cosy.feddb.core.security;

public enum Scope {
    LEARNING_RUN,
    TOOL_RUN,
    MODEL_PREDICTION_RUN,
    MODEL_WORKFLOW_RUN,
    PIPELINE_RUN,
    // A built tool started by the platform as a developer's test engine; the run id is the app id
    TEST_ENGINE
}
