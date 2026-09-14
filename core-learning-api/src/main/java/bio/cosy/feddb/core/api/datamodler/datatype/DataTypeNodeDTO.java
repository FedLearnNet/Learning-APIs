package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.BaseNodeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jNode;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jNode(label = "DataType")
public class DataTypeNodeDTO extends BaseNodeDTO {

    private String name;
    private String description;

    private List<String> options = new ArrayList<>();

    private List<DataTypeValidationDTO> validations = new ArrayList<>();

    private DataTypes type;

    private List<String> ontologyIds = new ArrayList<>();
    private List<String> schemaIds = new ArrayList<>();

    private Boolean isRequired = false;
    private Boolean allowNullValues = true;

    public void setType(String type) {
        this.type = DataTypes.valueOf(type);
    }

    public void setType(DataTypes type) {
        this.type = type;
    }

    public static String nodeLabel() {
        return DataTypeNodeDTO.label(DataTypeNodeDTO.class);
    }

    @JsonIgnore
    public boolean allowNullValues() {
        return Boolean.TRUE.equals(allowNullValues);
    }

    @JsonIgnore
    public boolean isRequired() {
        return Boolean.TRUE.equals(isRequired);
    }
}
