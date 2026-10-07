package bio.cosy.feddb.local.agent.etl.transform.assistant;

import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TransformerAssistantMessageDTO {
    private TransformerAssistantMessageType type;
    private ConnectorDTO connector;
    private String message;
    private ModelWorkflowChatMessageDTO chatMessage;
    private Long schemaId;
    private String mappingPath;
    private ConnectorSelectedTransformer selectedTransformer;
    private VisitMapping visitMapping;
    private List<Long> schemaIds;
    private List<String> columns;

    public TransformerAssistantMessageDTO(TransformerAssistantMessageType type) {
        this.type = type;
    }

    public static TransformerAssistantMessageDTO node(TransformerAssistantMessageType type, Long schemaId, String mappingPath,
                                                      ConnectorSelectedTransformer selectedTransformer) {
        TransformerAssistantMessageDTO message = new TransformerAssistantMessageDTO(type);
        message.setSchemaId(schemaId);
        message.setMappingPath(mappingPath);
        message.setSelectedTransformer(selectedTransformer);
        return message;
    }

    public static TransformerAssistantMessageDTO patientId(String mappingPath, String column) {
        TransformerAssistantMessageDTO message = new TransformerAssistantMessageDTO(TransformerAssistantMessageType.PATIENT_ID);
        message.setMappingPath(mappingPath);
        message.setColumns(List.of(column));
        return message;
    }

    public static TransformerAssistantMessageDTO chat(ModelWorkflowChatMessageDTO chatMessage) {
        TransformerAssistantMessageDTO message = new TransformerAssistantMessageDTO(TransformerAssistantMessageType.CHAT_MESSAGE);
        message.setChatMessage(ModelWorkflowChatMessageDTO.copy(chatMessage));
        return message;
    }
}
