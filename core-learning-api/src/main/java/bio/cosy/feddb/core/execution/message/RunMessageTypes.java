package bio.cosy.feddb.core.execution.message;

public enum RunMessageTypes {

    METRIC("metric"), LOG("log");

    private final String name;

    RunMessageTypes(String s) {
        name = s;
    }

    public String toString() {
        return this.name.toUpperCase();
    }
}
