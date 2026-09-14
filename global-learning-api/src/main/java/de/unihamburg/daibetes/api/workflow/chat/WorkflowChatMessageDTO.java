package de.unihamburg.daibetes.api.workflow.chat;

import bio.cosy.feddb.core.api.model.workflow.chat.UiActionDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
public class WorkflowChatMessageDTO {
    private String message;
    private boolean isRequest = false;
    private boolean isDone;
    private String errorMessage;
    private String statusMessage;

    private Long workflowId;
    private String id;

    private UiActionDTO action;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date createdAt;

    public WorkflowChatMessageDTO(Long workflowId){
        this.workflowId = workflowId;
        this.id = UUID.randomUUID().toString();
        this.createdAt = new Date();
        this.isDone = false;
    }

    public static WorkflowChatMessageDTO copy(WorkflowChatMessageDTO source) {
        WorkflowChatMessageDTO copy = new WorkflowChatMessageDTO();
        copy.setCreatedAt(source.getCreatedAt());
        copy.setMessage(source.getMessage());
        copy.setDone(source.isDone());
        copy.setId(source.getId());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setStatusMessage(source.getStatusMessage());
        copy.setWorkflowId(source.getWorkflowId());
        copy.setAction(source.getAction());
        return copy;
    }
}
