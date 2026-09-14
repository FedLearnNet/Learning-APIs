package bio.cosy.feddb.core.api.app.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode
public class TabularSchemaDTO {
    private Integer minRows;
    private Integer maxRows;
    private Integer minColumns;
    private Integer maxColumns;

    private Boolean allowOnlyNumbers;
    private Boolean prohibitedNulls;
    private NullValuePolicyDTO nullPolicy;

    private List<String> requiredColumns;

    private Map<String, ColumnRuleDTO> columns;
}
