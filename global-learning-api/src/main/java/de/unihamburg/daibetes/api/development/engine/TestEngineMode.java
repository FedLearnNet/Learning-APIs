package de.unihamburg.daibetes.api.development.engine;

public enum TestEngineMode {
    // Serves test runs, experiments and federated test runs until stopped, like a local engine
    ENGINE,
    // Runs once with the input the app generates in test mode, then exits
    SELF_TEST,
    // Serves one test run, then stops
    SINGLE_RUN;

    // Whether the container connects to the platform as the app's engine
    public boolean connects() {
        return this != SELF_TEST;
    }
}
