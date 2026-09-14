package de.unihamburg.daibetes.api.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaSubscriptions {
    private String schemaId;
    private List<String> subscriptions;
}
