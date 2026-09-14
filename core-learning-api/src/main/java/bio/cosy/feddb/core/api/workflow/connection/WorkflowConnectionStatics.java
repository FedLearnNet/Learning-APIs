package bio.cosy.feddb.core.api.workflow.connection;

public enum WorkflowConnectionStatics {
    INPUT_NODE_PREFIX("INPUT_NODE_");

    private final String value;

    WorkflowConnectionStatics(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String getInputNodeVariableName(String nodeId) {
        return INPUT_NODE_PREFIX.getValue() + nodeId;
    }
}
