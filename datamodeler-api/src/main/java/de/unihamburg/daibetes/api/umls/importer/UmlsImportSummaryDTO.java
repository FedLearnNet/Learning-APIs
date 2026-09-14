package de.unihamburg.daibetes.api.umls.importer;

import lombok.Data;

import java.util.Map;

@Data
public class UmlsImportSummaryDTO {

    private int totalNodes;

    private int totalEdges;

    private Map<String, Integer> nodesPerOntology;

    private int maxItems;

    private int batchSize;
}
