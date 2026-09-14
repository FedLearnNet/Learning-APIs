package bio.cosy.feddb.core.api.model.workflow.chat;

public enum ModelWorkflowChatMessageType {
    CHAT_MESSAGE,
    PREDICTION,
    WORKFLOW_PREDICTION;

    public static boolean isPrediction(ModelWorkflowChatMessageType type) {
        return type.equals(PREDICTION) || type.equals(WORKFLOW_PREDICTION);
    }
}
