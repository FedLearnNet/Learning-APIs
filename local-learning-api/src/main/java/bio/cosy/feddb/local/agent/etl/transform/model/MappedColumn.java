package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


@Getter
public class MappedColumn {

    private final String column;
    private final Set<String> values;
    private final DataTypes type;
    private final Set<String> allowedValues;
    private final List<ConnectorTransformerDTO> transformers;

    public MappedColumn(String column, Set<String> values, DataTypeNodeDTO dataType,
                        List<ConnectorTransformerDTO> transformers) {
        this.column = column;
        this.values = Set.copyOf(values);
        this.type = dataType.getType();
        this.allowedValues = allowedValues(dataType);
        this.transformers = List.copyOf(transformers);
    }

    public boolean fits(DataTypeNodeDTO dataType, Set<String> otherValues) {
        return type == dataType.getType() && allowedValues.equals(allowedValues(dataType)) && values.containsAll(otherValues);
    }

    private static Set<String> allowedValues(DataTypeNodeDTO dataType) {
        Set<String> allowed = new HashSet<>();
        if (dataType.getOptions() != null) {
            dataType.getOptions().stream().filter(Objects::nonNull).forEach(allowed::add);
        }
        return allowed;
    }
}
