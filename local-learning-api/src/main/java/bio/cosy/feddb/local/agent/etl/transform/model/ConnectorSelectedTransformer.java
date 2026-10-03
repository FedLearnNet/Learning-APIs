package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class ConnectorSelectedTransformer {
    @Description("""
            The selected transformer for the given schema node: moduleName and methodName exactly as listed,
            column for CELL functions, inputMapping (parameter -> source column) and returnMapping
            (return key -> output column) for ROW and PATIENT functions, hyperparams for the remaining parameters.
            Leave it empty when a source column can be mapped without transformation.
            """)
    private ConnectorTransformerDTO selectedTransformer;

    @Description("""
            The column that is mapped onto the schema node: the source column for a direct mapping,
            otherwise the column holding the transformer result.
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

}
