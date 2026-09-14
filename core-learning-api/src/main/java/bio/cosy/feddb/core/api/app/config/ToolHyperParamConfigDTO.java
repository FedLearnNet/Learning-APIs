package bio.cosy.feddb.core.api.app.config;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolHyperParamConfigDTO extends BaseDTO {
    private String name;
    private String variableName;
    private ToolConfigHyperParamDataType type;
    private String description;

    @JsonProperty("default")
    private String defaultValue;

    private ToolConfigModeType mode = ToolConfigModeType.BOTH;

    private List<String> options = new ArrayList<>();
    private Float minValue;
    private Float maxValue;
    private String pattern;


    public boolean isEqualTo(ToolHyperParamConfigDTO other) {
        if (other == null) return false;
        if (!this.name.equals(other.name)) return false;
        if (this.type != other.type) return false;
        if (!this.description.equals(other.description)) return false;
        if (!this.defaultValue.equals(other.defaultValue)) return false;
        if (this.mode != other.mode) return false;
        if (this.options.size() != other.options.size()) return false;
        for (int i = 0; i < this.options.size(); i++) {
            if (!this.options.get(i).equals(other.options.get(i))) return false;
        }
        if (this.minValue == null && other.minValue != null) return false;
        if (this.minValue != null && !this.minValue.equals(other.minValue)) return false;
        if (this.maxValue == null && other.maxValue != null) return false;
        if (this.maxValue != null && !this.maxValue.equals(other.maxValue)) return false;
        if (this.pattern == null && other.pattern != null) return false;
        if (this.pattern != null && !this.pattern.equals(other.pattern)) return false;

        return true;
    }

}
