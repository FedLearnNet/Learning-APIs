package bio.cosy.feddb.core.execution.helper;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;

//TODO CHANGE THAT THEIR ARE ALSO SAVED IN THE NEW FORMAT
/** Reads a workflow node's hyper parameters, which Local and Global store as a JSON string; none without a node. */
public final class NodeHyperParams {

    private NodeHyperParams() {
    }

    public static LinkedHashMap<String, Object> of(ObjectMapper objectMapper, BaseWorkflowNodeEntity node) {
        String hyperParams = node == null ? null : node.getHyperParams();
        if (hyperParams == null || hyperParams.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(hyperParams,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Hyper parameters of node " + node.getNodeId() + " are not valid JSON", e);
        }
    }
}
