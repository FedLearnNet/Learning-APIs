package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Row-level built-in that joins several source columns into one target column,
 * reversing a site that split a single variable across multiple fields (e.g. an
 * age band exported as separate lower/upper boundary columns).
 * <p>
 * Wiring (declarative, in the connector):
 * <ul>
 *   <li>{@code inputMapping}: maps each source column onto a row key of the same
 *       name, making the values available to the function;</li>
 *   <li>{@code hyperparams.delimiter}: the separator used to join;</li>
 *   <li>{@code hyperparams.parts}: a JSON array of the row keys to join, in order;</li>
 *   <li>{@code returnMapping}: {@code {"value": "<target column>"}} — the joined
 *       result is written back under {@code value}.</li>
 * </ul>
 * This is the row-based inverse of {@link SplitColumnFunction}.
 */
@ApplicationScoped
public class MergeColumnsFunction extends AbstractManagedRowFunction {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String methodName() {
        return "Merge Columns";
    }

    @Override
    public String description() {
        return "Join several columns into one by a delimiter (row-level inverse of Split Column).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("delimiter", FunctionParameterType.STRING, true, null,
                        "Separator used to join the source columns."),
                FunctionParameterDTO.of("parts", FunctionParameterType.MAP, true, null,
                        "JSON array of row keys to join, in order.")
        );
    }

    @Override
    public Map<String, Object> execute(RowFunctionExecutionContext context) {
        Map<String, Object> row = context.getRow();
        Map<String, Object> params = context.getParams();

        String delimiter = String.valueOf(params.get("delimiter"));
        List<String> parts;
        try {
            parts = objectMapper.readValue(String.valueOf(params.get("parts")), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 'parts' JSON array", e);
        }

        String merged = parts.stream()
                .map(part -> {
                    Object value = row.get(part);
                    return value == null ? "" : value.toString();
                })
                .collect(Collectors.joining(delimiter));
        return Map.of("value", merged);
    }
}
