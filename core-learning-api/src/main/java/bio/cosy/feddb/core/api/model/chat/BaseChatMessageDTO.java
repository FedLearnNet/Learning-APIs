package bio.cosy.feddb.core.api.model.chat;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseChatMessageDTO extends BaseDTO {
    private String message;

    private boolean isRequest;
    private boolean isDone;

    private String errorMessage;
    private String statusMessage;

    public void addChunk(String chunk) {
        if (this.message == null) {
            this.message = chunk;
        } else {
            this.message += chunk;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseChatMessageDTO> T copy(T source) {
        T copy;
        try {
            copy = (T) source.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            copy = (T) new BaseChatMessageDTO();
        }
        copy.setId(source.getId());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setVersion(source.getVersion());
        copy.setMessage(source.getMessage());
        copy.setRequest(source.isRequest());
        copy.setDone(source.isDone());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setStatusMessage(source.getStatusMessage());
        return copy;
    }
}
