package de.unihamburg.daibetes.api.umls.search;

import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.ontology.OntologyBO;
import de.unihamburg.daibetes.api.umls.UMLSSources;
import de.unihamburg.daibetes.service.UMLSSearchService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class UMLSSearchBO {

    @Inject
    @RestClient
    UMLSSearchService umlsSearchClient;

    @Inject
    OntologyBO ontologyBO;

    @ConfigProperty(name = "umls.sab.filter", defaultValue = "SNOMEDCT_US,RXNORM,LNC,ICD10,NCBI")
    List<String> sabFilter;

    public Uni<PagedResponse<UMLSSearchResultDTO>> search(String searchString, int page, int pageSize, UMLSSources sources) {
        String sourcesParam = String.join(",", sabFilter);
        if(sources != null) {
            sourcesParam = sources.name();
        }
        return umlsSearchClient.searchConcepts(searchString, "code", sourcesParam, pageSize, page)
                .onFailure(WebApplicationException.class)
                .transform(throwable -> {
                    WebApplicationException wae = throwable;
                    if (wae.getResponse() != null && wae.getResponse().getStatus() == 404) {
                        return new NotFoundException("404 Resource Not Found");
                    }
                    return throwable;
                })
                .onItem().transform(wrapper -> {
                    PagedResponse<UMLSSearchResultDTO> dto = new PagedResponse<>(page, pageSize);

                    if (wrapper == null ||
                            wrapper.getResult() == null ||
                            wrapper.getResult().getResults() == null ||
                            wrapper.getResult().getResults().isEmpty()) {
                        return dto;
                    }
                    dto.setTotalCount(wrapper.getResult().getRecCount());
                    dto.setResults(wrapper.getResult().getResults());
                    return dto;
                });
    }

    public Uni<UMLSDetailResultDTO> getItem(String itemId, UMLSSources source) {
        return umlsSearchClient.getItem(source.name(), itemId)
                .onFailure(WebApplicationException.class)
                .transform(throwable -> {
                    WebApplicationException wae = throwable;
                    if (wae.getResponse() != null && wae.getResponse().getStatus() == 404) {
                        return new NotFoundException("404 Resource Not Found");
                    }
                    return throwable;
                })
                .onItem().transform(wrapper -> {
                    if (wrapper == null || wrapper.getResult() == null) {
                        return null;
                    }
                    return wrapper.getResult();
                });
    }

    public Uni<List<UMLSDetailResultDTO>> getParents(String itemId, UMLSSources source) {
        return umlsSearchClient.getParents(source.name(), itemId, 100)
                .onItem().transform(wrapper -> {
                    if (wrapper == null || wrapper.getResult() == null || wrapper.getResult().isEmpty()) {
                        return Collections.emptyList();
                    }
                    return wrapper.getResult();
                });
    }

    public Uni<List<UMLSCytoscapeGraphElementDTO>> createCytoscapeGraph(String startNodeId, UMLSSources source) {
        List<UMLSCytoscapeGraphElementDTO> elements = new CopyOnWriteArrayList<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Set<String> nodeIds = ConcurrentHashMap.newKeySet();
        Set<String> edgeKeys = ConcurrentHashMap.newKeySet();

        return getItem(startNodeId, source)
                .onItem().transformToUni(firstItem -> {
                    // Add root node (if found)
                    UMLSCytoscapeGraphElementDTO rootNode;
                    if (firstItem != null) {
                        rootNode = new UMLSCytoscapeGraphElementDTO(firstItem.getUi(), firstItem.getName());
                        nodeIds.add(firstItem.getUi());
                    } else {
                        // Fallback node if UMLS did not return details
                        rootNode = new UMLSCytoscapeGraphElementDTO(startNodeId, startNodeId);
                        nodeIds.add(startNodeId);
                    }
                    elements.add(rootNode);
                    return ontologyBO.isUMLSCodeInDB(rootNode.getId())
                            .invoke(rootNode::setInDB)
                            .chain(() ->
                                    traverseAndAddNodesForGraph(
                                            startNodeId,
                                            source,
                                            elements,
                                            visited,
                                            nodeIds,
                                            edgeKeys
                                    )
                            )
                            .replaceWith(elements);
                });
    }

    private Uni<Void> traverseAndAddNodesForGraph(
            String currentNodeId,
            UMLSSources source,
            List<UMLSCytoscapeGraphElementDTO> elements,
            Set<String> visited,
            Set<String> nodeIds,
            Set<String> edgeKeys
    ) {
        if (visited.contains(currentNodeId)) {
            return Uni.createFrom().voidItem();
        }
        visited.add(currentNodeId);

        return getParents(currentNodeId, source)
                .onItem().transformToUni(parents -> {
                    List<Uni<Void>> unis = new ArrayList<>();

                    for (UMLSDetailResultDTO parent : parents) {
                        Uni<Void> uni = Uni.createFrom().voidItem()
                                .onItem().transformToUni(ignored -> {
                                    // Add parent node (id + name)
                                    UMLSCytoscapeGraphElementDTO node = new UMLSCytoscapeGraphElementDTO(parent.getUi(), parent.getName());
                                    boolean newNodeAdded = false;
                                    if (nodeIds.add(parent.getUi())) {
                                        elements.add(node);
                                        newNodeAdded = true;
                                    }

                                    // Add edge currentNodeId -> parent.ui
                                    String edgeKey = currentNodeId + "->" + parent.getUi();
                                    if (edgeKeys.add(edgeKey)) {
                                        elements.add(new UMLSCytoscapeGraphElementDTO(edgeKey, currentNodeId, parent.getUi()));
                                    }

                                    // Recurse if parent has further parents
                                    if (parent.getParents() != null && !"NONE".equals(parent.getParents())) {
                                        if (newNodeAdded) {


                                            return traverseAndAddNodesForGraph(
                                                    parent.getUi(),
                                                    UMLSSources.valueOf(parent.getRootSource()),
                                                    elements,
                                                    visited,
                                                    nodeIds,
                                                    edgeKeys
                                            );
                                        } else {
                                            return ontologyBO.isUMLSCodeInDB(node.getId())
                                                    .invoke(node::setInDB)
                                                    .chain(() ->
                                                            traverseAndAddNodesForGraph(
                                                                    parent.getUi(),
                                                                    UMLSSources.valueOf(parent.getRootSource()),
                                                                    elements,
                                                                    visited,
                                                                    nodeIds,
                                                                    edgeKeys
                                                            )
                                                    );
                                        }
                                    }
                                    return Uni.createFrom().voidItem();
                                });

                        unis.add(uni);
                    }

                    return combineAllVoid(unis);
                });
    }

    private Uni<Void> combineAllVoid(List<Uni<Void>> unis) {
        if (unis.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return Uni.combine().all().unis(unis).discardItems();
    }


}
