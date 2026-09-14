package bio.cosy.feddb.core.api.app.config;

import lombok.Data;

import java.util.List;

@Data
public class ColumnRuleDTO {
    private ToolConfigHyperParamDataType type;
    private Boolean nullable;
    private String regex;
    private List<String> enumValues;
    private Double min;
    private Double max;
    private String description;
}
