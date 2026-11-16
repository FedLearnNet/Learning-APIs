package bio.cosy.feddb.core.execution.run.types;

public enum AppRunTypeEnum {
    TEST_RUN,
    EXPERIMENT_RUN,
    PROJECT_RUN,
    FEDERATED_RUN,
    FEDERATED_TEST_RUN,
    MODEL_RUN,
    NOT_DEFINED;

    public boolean isFederated() {
        return this == FEDERATED_RUN || this == FEDERATED_TEST_RUN;
    }
}
