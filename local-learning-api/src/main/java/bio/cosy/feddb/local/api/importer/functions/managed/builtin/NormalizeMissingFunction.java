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
import java.util.Locale;
import java.util.Map;

/**
 * Collapse a site's heterogeneous missing-value sentinels onto a single
 * convention. Different institutions encode "unknown" differently — {@code ?},
 * {@code NULL}, {@code N/A}, {@code -99}, {@code unknown}, empty — which the
 * shared schema cannot validate. This function maps any configured sentinel
 * (compared case-insensitively after trimming) to {@code replacement} (empty by
 * default), so the importer treats it as an absent value for non-nullable nodes.
 * Real values pass through untouched.
 */
@ApplicationScoped
public class NormalizeMissingFunction extends AbstractManagedCellFunction {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String methodName() {
        return "Normalize Missing";
    }

    @Override
    public String description() {
        return "Map any of a set of missing-value sentinels (JSON array) to a single replacement (default empty).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("tokens", FunctionParameterType.MAP, true, null,
                        "JSON array of sentinel strings to treat as missing (e.g. [\"?\", \"N/A\", \"-99\"])."),
                FunctionParameterDTO.of("replacement", FunctionParameterType.STRING, false, "",
                        "Value to write when a sentinel is matched (default empty string).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        String replacement = params.get("replacement") == null ? "" : String.valueOf(params.get("replacement"));

        Object value = context.getValue();
        if (value == null) {
            return replacement;
        }
        String candidate = value.toString().strip().toLowerCase(Locale.ROOT);

        List<String> tokens;
        try {
            tokens = objectMapper.readValue(String.valueOf(params.get("tokens")), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 'tokens' JSON array", e);
        }

        for (String token : tokens) {
            if (token != null && token.strip().toLowerCase(Locale.ROOT).equals(candidate)) {
                return replacement;
            }
        }
        return value;
    }
}
