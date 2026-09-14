package bio.cosy.feddb.local.api.importer.functions;

import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class FunctionRegistry {

    private final Map<String, AbstractManagedFunction> functionMap = new LinkedHashMap<>();

    @Inject
    public FunctionRegistry(Instance<AbstractManagedFunction> functions) {
        for (AbstractManagedFunction function : functions) {
            functionMap.put(key(function.module(), function.methodName()), function);
        }
    }

    public List<AbstractManagedFunction> getAll() {
        return new ArrayList<>(functionMap.values());
    }

    public Optional<AbstractManagedFunction> get(String module, String methodName) {
        return Optional.ofNullable(functionMap.get(key(module, methodName)));
    }

    public Map<String, List<String>> getGroupedMethods() {
        return functionMap.values().stream()
                .collect(Collectors.groupingBy(
                        AbstractManagedFunction::module,
                        LinkedHashMap::new,
                        Collectors.mapping(AbstractManagedFunction::methodName, Collectors.toList())
                ));
    }

    private String key(String module, String methodName) {
        return module + "::" + methodName;
    }
}
