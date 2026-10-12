package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

import static bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction.VALUE_PREFIX;
import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics.CODE_MODULE;
import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics.TOOLS_MODULE;
import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics.plain;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorTransformerDTO extends BaseDTO {

    private String moduleName;
    private String methodName;
    private Map<String, Object> inputMapping;
    private Map<String, Object> returnMapping;
    private String column;
    private Integer position;

    // For remote app execution
    private Long appVersionId;
    private String appImage;
    private Map<String, Object> hyperparams;

    private Long connectorId;


    @JsonIgnore
    public boolean isAppBased() {
        return getAppImage() != null && !getAppImage().isBlank();
    }

    @JsonIgnore
    public boolean isCodeTransformer() {
        return getModuleName() != null && plain(CODE_MODULE).equals(plain(getModuleName()));
    }

    @JsonIgnore
    public boolean isStoreTool() {
        return TOOLS_MODULE.equals(getModuleName())
                || isCodeTransformer()
                || getAppVersionId() != null
                || isAppBased();
    }

    @JsonIgnore
    public boolean isRowBased() {
        return getReturnMapping() != null && !getReturnMapping().isEmpty()
                || getInputMapping() != null && getInputMapping().values().stream()
                .anyMatch(value -> !String.valueOf(value).startsWith(VALUE_PREFIX));
    }

    @JsonIgnore
    public boolean isCellBased() {
        return getColumn() != null && !getColumn().isBlank() && !isRowBased() && !isAppBased();
    }

}
