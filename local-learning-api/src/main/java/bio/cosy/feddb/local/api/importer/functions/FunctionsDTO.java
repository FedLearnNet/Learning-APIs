package bio.cosy.feddb.local.api.importer.functions;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class FunctionsDTO {
    private Map<String, List<String>> modules = new LinkedHashMap<>();

    public void addModuleMethods(String module, List<String> methods) {
        modules.put(module, methods);
    }
}
