package de.unihamburg.daibetes.embedding;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class OntologyToDocumentParser {

    public static Document toDocument(OntologyNodeDTO node) {
        String text = buildNodeText(node);

        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put("nodeId", node.getId() != null ? node.getId().toString() : "");
        metadataMap.put("cui", nullToEmpty(node.getCui()));
        metadataMap.put("lat", nullToEmpty(node.getLat()));

        Metadata metadata = Metadata.from(metadataMap);

        return Document.from(text, metadata);
    }

    public static String buildNodeText(OntologyNodeDTO node) {
        String names = node.getNames() != null ? String.join(", ", node.getNames()) : "";
        String description = nullToEmpty(node.getDescription());
        String codes = node.getCodes() != null ? String.join(", ", node.getCodes()) : "";
        String auis = node.getAuis() != null ? String.join(", ", node.getAuis()) : "";
        String sabs = node.getSabs() != null ? String.join(", ", node.getSabs()) : "";
        String cui = nullToEmpty(node.getCui());
        if (StringUtils.isEmpty(description)) {
            return names;
        }

        return """
                Names: %s
                Description: %s
                Codes: %s
                AUIS: %s
                SABS: %s
                CUI: %s
                """.formatted(
                names,
                description,
                codes,
                auis,
                sabs,
                cui
        );
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
