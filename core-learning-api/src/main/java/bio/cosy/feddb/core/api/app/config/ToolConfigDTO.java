package bio.cosy.feddb.core.api.app.config;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolConfigDTO extends BaseDTO {
    private String name;
    private String variableName;

    private ToolConfigDataType type;
    private String description;

    private ToolConfigModeType mode = ToolConfigModeType.BOTH;

    //FOR CSV or TSV
    private String delimiter;
    private Boolean hasHeader;
    private Integer indexCol;

    private String minValue;
    private String maxValue;
    private String shape;

    private TabularSchemaDTO tabularSchema;
}
