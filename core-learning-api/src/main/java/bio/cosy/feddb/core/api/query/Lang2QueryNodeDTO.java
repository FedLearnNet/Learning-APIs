package bio.cosy.feddb.core.api.query;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * One node of a Lang2Query document: either a boolean group ({@code $and}/{@code $or})
 * or a single leaf condition. Extra paper fields are accepted so the compiler can
 * reject them instead of silently dropping them.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Lang2QueryNodeDTO {

    @JsonProperty("connecting_operator")
    @JsonAlias("connectingOperator")
    private String connectingOperator;
    private List<Lang2QueryNodeDTO> conditions;

    @JsonProperty("ontology_id")
    @JsonAlias("ontologyId")
    private String ontologyId;
    @JsonProperty("data_type_id")
    @JsonAlias({"dataTypeId", "datatype_id"})
    private String dataTypeId;
    @JsonAlias("datatype")
    private String datatype;
    private String operator;
    private Object value;

    @JsonProperty("temporal_relations")
    @JsonAlias("temporalRelations")
    private List<TemporalRelationDTO> temporalRelations;
    @JsonProperty("value_mode")
    @JsonAlias("valueMode")
    private String valueMode;
    @JsonProperty("time_filter")
    @JsonAlias("timeFilter")
    private Object timeFilter;
    @JsonProperty("time_aggregation_method")
    @JsonAlias("timeAggregationMethod")
    private String timeAggregationMethod;
    @JsonProperty("value_aggregation_method")
    @JsonAlias("valueAggregationMethod")
    private String valueAggregationMethod;
    @JsonProperty("at_least")
    @JsonAlias("atLeast")
    private Integer atLeast;
    @JsonProperty("at_most")
    @JsonAlias("atMost")
    private Integer atMost;
    private String scale;

    @JsonIgnore
    public boolean isGroup() {
        return connectingOperator != null && !connectingOperator.isBlank();
    }

    @JsonIgnore
    public String valueAsString() {
        return value == null ? null : String.valueOf(value);
    }
}
