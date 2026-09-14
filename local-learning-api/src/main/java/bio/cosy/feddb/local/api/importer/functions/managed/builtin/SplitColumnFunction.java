package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Row-level built-in that splits one packed source column into several target
 * columns, reversing a site that concatenated distinct variables into a single
 * field (e.g. {@code diag_1|diag_2|diag_3} or hyphen-joined encounter codes).
 * <p>
 * Wiring (declarative, in the connector):
 * <ul>
 *   <li>{@code inputMapping}: {@code {"source": "<packed column>"}} — the column
 *       to split is read from the row under the key {@code source};</li>
 *   <li>{@code hyperparams.delimiter}: the separator to split on;</li>
 *   <li>{@code hyperparams.parts}: a JSON array naming the output pieces in order;
 *       each name is also a {@code returnMapping} key pointing at the destination
 *       column.</li>
 * </ul>
 * This is the row-based counterpart to the cell-based built-ins: it reads and
 * writes the whole row rather than a single cell, and is the inverse of
 * {@link MergeColumnsFunction}.
 */
@ApplicationScoped
public class SplitColumnFunction extends AbstractManagedRowFunction {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String methodName() {
        return "Split Column";
    }

    @Override
    public String description() {
        return "Split one packed column into several columns by a delimiter (row-level inverse of Merge Columns).";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("source", FunctionParameterType.STRING, true, null,
                        "Row key carrying the packed value to split (mapped via inputMapping)."),
                FunctionParameterDTO.of("delimiter", FunctionParameterType.STRING, true, null,
                        "Separator to split the packed value on."),
                FunctionParameterDTO.of("parts", FunctionParameterType.MAP, true, null,
                        "JSON array of output piece names, in order (each name is also a returnMapping key).")
        );
    }

    @Override
    public Map<String, Object> execute(RowFunctionExecutionContext context) {
        Map<String, Object> row = context.getRow();
        Map<String, Object> params = context.getParams();

        Object packed = row.get("source");
        String delimiter = String.valueOf(params.get("delimiter"));
        List<String> parts;
        try {
            parts = objectMapper.readValue(String.valueOf(params.get("parts")), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 'parts' JSON array", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (packed == null) {
            for (String part : parts) {
                result.put(part, null);
            }
            return result;
        }

        String[] pieces = packed.toString().split(Pattern.quote(delimiter), parts.size());
        for (int i = 0; i < parts.size(); i++) {
            result.put(parts.get(i), i < pieces.length ? pieces[i] : "");
        }
        return result;
    }
}
