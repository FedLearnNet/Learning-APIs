package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;

import java.util.*;

public class TransformerHelper {
    public static ConnectorDTO toConnector(Collection<TransformerAssistantMessageDTO> results) {
        List<ConnectorTransformerDTO> transformers = new ArrayList<>();
        List<ConnectorMappingDTO> mappings = new ArrayList<>();
        for (TransformerAssistantMessageDTO result : results) {
            if (result.getType() == TransformerAssistantMessageType.PATIENT_ID) {
                ConnectorMappingDTO mapping = new ConnectorMappingDTO();
                mapping.setColumn(result.getColumns().getFirst());
                mapping.setMapping(result.getMappingPath());
                mappings.add(mapping);
                continue;
            }
            ConnectorSelectedTransformer selected = result.getSelectedTransformer();
            if (selected == null || !selected.isFoundTransformer()) {
                continue;
            }
            transformers.addAll(selected.getSelectedTransformers());
            ConnectorMappingDTO mapping = new ConnectorMappingDTO();
            mapping.setColumn(selected.getMappedColumn());
            mapping.setMapping(result.getMappingPath());
            mapping.setSchemaId(result.getSchemaId());
            mappings.add(mapping);
        }
        ConnectorDTO connector = new ConnectorDTO();
        connector.setTransformer(transformers);
        connector.setSchemaMapping(mappings);
        return connector;
    }
}
