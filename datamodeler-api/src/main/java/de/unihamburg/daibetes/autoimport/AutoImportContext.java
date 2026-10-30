package de.unihamburg.daibetes.autoimport;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ontologies are matched by CUI, so an imported ontology may resolve to an existing node with another ID
 * (e.g. UMLS).
 */
public class AutoImportContext {

    private final Map<UUID, UUID> ontologyIds = new ConcurrentHashMap<>();

    public void mapOntology(UUID importId, UUID actualId) {
        if (importId != null && actualId != null) {
            ontologyIds.put(importId, actualId);
        }
    }

    public UUID resolveOntology(UUID importId) {
        return importId == null ? null : ontologyIds.getOrDefault(importId, importId);
    }

    public String resolveOntology(String importId) {
        return importId == null ? null : resolveOntology(UUID.fromString(importId)).toString();
    }

    public Collection<UUID> getImportedOntologyIds() {
        return Set.copyOf(ontologyIds.values());
    }
}
