package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.unihamburg.daibetes.api.schema.SchemaSubscriptions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryConfigDTO {
    private List<String> schemaIds;
    private int subscriptionCount = 0;
    private OntologyNodeDTO ontology;
    private DataTypeNodeDTO dataType;


    public QueryConfigDTO(SchemaSubscriptions schemaSubscription, SchemaNodeDetailDTO schema) {
        this.schemaIds = List.of(schemaSubscription.getSchemaId());
        if (schemaSubscription.getSubscriptions() != null) {
            this.subscriptionCount = schemaSubscription.getSubscriptions().size();
        }
        this.ontology = schema.getOntology();
        this.dataType = schema.getDataType();
    }

    @JsonIgnore
    public boolean hasSameData(QueryConfigDTO other) {
        if (other == null) return false;
        if (this.ontology == null || this.dataType == null || other.ontology == null || other.dataType == null) {
            return false;
        }
        return this.ontology.getId().equals(other.ontology.getId()) &&
                this.dataType.getId().equals(other.dataType.getId());
    }

    public void addAnotherConfig(QueryConfigDTO other) {
        if (other == null) return;
        if (!this.hasSameData(other)) {
            return;
        }
        for (String schemaId : other.schemaIds) {
            if (!this.schemaIds.contains(schemaId)) {
                this.schemaIds.add(schemaId);
            }
        }
        this.subscriptionCount += other.subscriptionCount;
    }

    public String getName() {
        return this.ontology.getNames().stream().findFirst().orElseGet(() -> this.dataType.getName());
    }

    public String getLabel() {
        String name = getName();
        return name + " (" + this.dataType.getName() + ")";
    }
}
