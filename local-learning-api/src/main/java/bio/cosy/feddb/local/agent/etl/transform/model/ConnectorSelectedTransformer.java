package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorSelectedTransformer {
    @Description("""
            The transformers for the given schema node in execution order; several form a chain where each one works
            on the result of the previous one. Each has moduleName and methodName exactly as listed, column for CELL
            functions, inputMapping (parameter -> source column) and returnMapping (return key -> output column) for ROW
            and PATIENT functions, hyperparams for the remaining parameters.
            Leave it empty when a source column can be mapped without transformation.
            """)
    private List<ConnectorTransformerDTO> selectedTransformers = new ArrayList<>();

    @Description("""
            The column that is mapped onto the schema node: the source column for a direct mapping,
            otherwise the column holding the result of the last transformer.
            """)
    private String mappedColumn;

    @Description("""
            Indicates whether a transformer (or a direct mapping) was found for the given schema node.
            """)
    private boolean foundTransformer;

    @Description("""
            In case no Transformer is found, this field explain what type of function would be needed
            """)
    private String neededTransformerFunction;

    public List<ConnectorTransformerDTO> getSelectedTransformers() {
        if (selectedTransformers == null) {
            selectedTransformers = new ArrayList<>();
        }
        return selectedTransformers;
    }

    public static ConnectorSelectedTransformer mapping(String column, List<ConnectorTransformerDTO> transformers) {
        ConnectorSelectedTransformer selected = new ConnectorSelectedTransformer();
        selected.setFoundTransformer(true);
        selected.setMappedColumn(column);
        selected.setSelectedTransformers(new ArrayList<>(transformers == null ? List.of() : transformers));
        return selected;
    }

    public static ConnectorSelectedTransformer notFound(String neededTransformerFunction) {
        ConnectorSelectedTransformer selected = new ConnectorSelectedTransformer();
        selected.setNeededTransformerFunction(neededTransformerFunction);
        return selected;
    }
}
