package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GenericValueMapperFunction extends AbstractManagedCellFunction {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String methodName() {
        return "Generic Value Mapper";
    }

    @Override
    public String description() {
        return "Map a value using a JSON map string.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("map", FunctionParameterType.MAP, true, null,
                        "JSON object mapping input values to output values.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        String key = value == null ? null : String.valueOf(value);
        String mapString = String.valueOf(context.getParams().get("map"));

        try {
            Map<String, Object> mapping = objectMapper.readValue(mapString, new TypeReference<>() {
            });
            return mapping.getOrDefault(key, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid map_string JSON", e);
        }
    }
}
