package bio.cosy.feddb.local.agent.etl.transform.assistant;

public enum TransformerAssistantMessageType {
    START,
    USER_MESSAGE,
    STOP,
    CHAT_MESSAGE,
    NODE_STARTED,
    NODE_RESULT,
    DONE,
    ERROR
}
