package eval.agent.etl.transform.pojo;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransformerWriterSuggestion {
    private List<TransformerAssistantMessageDTO> nodes;
    private ConnectorDTO connector;
}
