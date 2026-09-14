package bio.cosy.feddb.local.api.schema.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class DataTypeDTO extends BaseDTO {
    private String description;
    private String name;
    private DataTypes type;

    private DatatypeFormTypeEnum formType;

    @JsonAlias({"ontology_ids", "ontologyIds"})
    private List<String> ontologyIds;

    @JsonAlias({"unique_id", "globalId"})
    private String globalId;

    @JsonAlias({"schema_ids", "schemaIds"})
    private String[] schemaIds;

    private Set<DataTypeValidationDTO> validations;
    private Set<DataTypeOptionsDTO> options;

    private Boolean allowNullValues = true;
    private Boolean isRequired = false;

    // Only contains strings for categorical data types
    // e.g. numerical uses min and max validations
    private Set<Object> allowedValues;

    // This is a mapping of the allowed values to their human-readable form
    // e.g. from "codeX" to "Colorectal Cancer"
    private HashMap<String, String> mapping;
}
