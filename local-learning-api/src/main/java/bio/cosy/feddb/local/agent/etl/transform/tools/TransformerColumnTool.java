package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequestScoped
public class TransformerColumnTool {

    private static final int MAX_RELATED_COLUMNS = 10;
    private static final int DESCRIBED_VALUES = 10;

    @Inject
    ObjectMapper objectMapper;

    private TransformerRunContext context;
    private List<String> columns;

    public void init(TransformerRunContext context, List<String> columns) {
        this.context = context;
        this.columns = columns;
    }

    @Tool("""
            The values of columns in the whole file with their counts. Use it to look at columns beyond the ones shown,
            e.g. whether a column holds the information under another name, in codes or another language, or packed
            together with other values in one cell.
            """)
    public String describeColumns(@P("The exact column names") List<String> names) {
        context.step("describeColumns %s", names);
        List<String> unknown = names == null ? List.of() : names.stream().filter(name -> !columns.contains(name)).toList();
        if (names == null || names.isEmpty() || !unknown.isEmpty()) {
            return "Unknown columns " + unknown + ", the columns are " + columns;
        }
        String described = TransformerHelper.describeFileValues(names, context.fileProfiles());
        return described.isBlank() ? "No statistics for " + names : described;
    }


    public String relatedColumns(String name) {
        Set<String> values = Set.copyOf(fileValues(name));
        Map<String, Long> shared = columns.stream()
                .filter(column -> !column.equals(name) && !column.equals(context.patientIdColumn()))
                // in file order, for columns sharing as many values
                .collect(Collectors.toMap(column -> column, column -> fileValues(column).stream().filter(values::contains).count(),
                        Long::sum, LinkedHashMap::new));
        return shared.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && 2 * entry.getValue() >= values.size())
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(MAX_RELATED_COLUMNS)
                .map(Map.Entry::getKey)
                .map(column -> "- " + column + " " + TransformerHelper.describeFileValues(context.fileProfiles().get(column),
                        DESCRIBED_VALUES) + mapping(column))
                .collect(Collectors.joining("\n"));
    }

    private String mapping(String column) {
        List<ConnectorTransformerDTO> reading = context.config().getTransformer().stream()
                .filter(transformer -> ConnectorHelper.transformerInputs(transformer).contains(column))
                .toList();
        List<String> nodes = Stream.concat(reading.stream()
                        .flatMap(transformer -> ConnectorHelper.transformerOutputs(transformer).stream()), Stream.of(column))
                .map(context.mappedColumns()::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (nodes.isEmpty()) {
            return ", not mapped yet";
        }
        return ", mapped onto " + String.join(", ", nodes)
                + (reading.isEmpty() ? " directly" : " with " + reading.stream().map(this::describe).toList());
    }

    private String describe(ConnectorTransformerDTO transformer) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("column", transformer.getColumn());
        settings.put("inputMapping", transformer.getInputMapping());
        settings.put("hyperparams", transformer.getHyperparams());
        return transformer.getMethodName() + " " + objectMapper.valueToTree(settings);
    }

    private List<String> fileValues(String column) {
        return TransformerHelper.fileValues(context.fileProfiles().get(column)).stream()
                .filter(value -> !RowProfiler.isMissing(value)).toList();
    }
}
