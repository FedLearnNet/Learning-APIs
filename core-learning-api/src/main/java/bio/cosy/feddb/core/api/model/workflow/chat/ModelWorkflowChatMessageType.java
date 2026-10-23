package bio.cosy.feddb.core.api.model.workflow.chat;

public enum ModelWorkflowChatMessageType {
    CHAT_MESSAGE,
    PREDICTION,
    WORKFLOW_PREDICTION,
    RUN; //An execution run, the message is the run with its steps and attempts

    public static boolean isPrediction(ModelWorkflowChatMessageType type) {
        return type.equals(PREDICTION) || type.equals(WORKFLOW_PREDICTION);
    }
}
