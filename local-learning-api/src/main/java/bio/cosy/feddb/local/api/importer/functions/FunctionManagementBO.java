package bio.cosy.feddb.local.api.importer.functions;

import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class FunctionManagementBO {

    @Inject
    FunctionRegistry registry;

    public FunctionsDTO getAll() {
        FunctionsDTO dto = new FunctionsDTO();
        registry.getGroupedMethods().forEach(dto::addModuleMethods);
        return dto;
    }

    public List<FunctionsDetailDTO> getAllDetail() {
        return registry.getAll().stream()
                .map(this::toDetail)
                .toList();
    }

    public FunctionsDetailDTO get(String module, String methodName) {
        AbstractManagedFunction function = registry.get(module, methodName)
                .orElseThrow(() -> new NotFoundException("Function not found"));
        return toDetail(function);
    }

    private FunctionsDetailDTO toDetail(AbstractManagedFunction function) {
        FunctionsDetailDTO dto = new FunctionsDetailDTO();
        dto.setModuleName(function.module());
        dto.setMethodName(function.methodName());
        dto.setDescription(function.description());
        dto.setMode(function.mode());
        dto.setParameters(function.parameters());
        dto.setReturnKeys(function.returnKeys());
        return dto;
    }
}
