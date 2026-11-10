package bio.cosy.feddb.core.security;

public enum Scope {
    EXECUTION_ATTEMPT,
    LEARNING_RUN,
    TOOL_RUN,
    MODEL_PREDICTION_RUN,
    MODEL_WORKFLOW_RUN,
    PIPELINE_RUN,
    // The test engine container of an app, connecting as the APP of /testembed/{appId}; the run id is the app id
    TEST_EMBED_APP
}
