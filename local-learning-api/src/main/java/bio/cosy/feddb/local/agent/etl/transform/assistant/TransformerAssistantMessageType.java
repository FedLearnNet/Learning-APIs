package bio.cosy.feddb.local.agent.etl.transform.assistant;

public enum TransformerAssistantMessageType {
    START,
    USER_MESSAGE,
    STOP,
    APPLY,
    APPLIED,
    CHAT_MESSAGE,
    NODE_PENDING,
    NODE_STARTED,
    NODE_RESULT,
    PATIENT_ID,
    DONE,
    ERROR
}
