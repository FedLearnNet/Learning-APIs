package bio.cosy.feddb.local.api.workflow.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class HyperParamConfigToYml {

    public static String createYaml(LinkedHashMap<String, Object> hyperParams) throws JsonProcessingException {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : hyperParams.entrySet()) {
            String varName = entry.getKey();
            Object defaultValue = entry.getValue();
            String[] parts = varName.split("__");
            if (parts.length == 0) {
                parts = varName.split("\\.");
            }
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String key = parts[i];
                Object child = current.get(key);
                if (!(child instanceof Map)) {
                    Map<String, Object> newMap = new LinkedHashMap<>();
                    current.put(key, newMap);
                    current = newMap;
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) child;
                    current = childMap;
                }
            }
            current.put(parts[parts.length - 1], defaultValue);
        }

        YAMLMapper mapper = new YAMLMapper();
        return mapper.writeValueAsString(root);
    }
}
