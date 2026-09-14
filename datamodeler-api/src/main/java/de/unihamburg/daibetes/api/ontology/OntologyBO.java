package de.unihamburg.daibetes.api.ontology;


import bio.cosy.feddb.core.api.datamodler.ontology.*;
import bio.cosy.feddb.core.base.PagedResponse;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class OntologyBO {

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    OntologyRAG ontologyRAG;

    public Uni<Boolean> isUMLSCodeInDB(String code) {
        return ontologyDAO.existsByCodeId(code);
    }

    public Uni<PagedResponse<OntologyNodeDTO>> list(String search, int page, int pageSize) {

        boolean hasSearch = StringUtils.isNotBlank(search);

        Uni<List<OntologyNodeDTO>> items = hasSearch
                ? ontologyDAO.findAll(search, page, pageSize).collect().asList()
                : ontologyDAO.findAll(page, pageSize).collect().asList();

        Uni<Long> total = hasSearch
                ? ontologyDAO.countAll(search)
                : ontologyDAO.countAll();

        return Uni.combine().all().unis(items, total)
                .asTuple()
                .map(t -> new PagedResponse<>(
                        t.getItem1(),
                        page,
                        pageSize,
                        t.getItem2()
                ));
    }

    public Uni<List<OntologySearchResponseDTO>> searchEmbedding(String search, int maxResults) {
        Log.info("Starting embedding search for: " + search);
        if (StringUtils.isBlank(search)) {
            return Uni.createFrom().item(new ArrayList<>());
        }
        Uni<OntologySearchResponseDTO> searchResult = ontologyDAO.searchById(search)
                .onFailure().recoverWithNull()
                .onItem().transform(node -> {
                    if (node != null) {
                        return new OntologySearchResponseDTO(node);
                    }
                    return null;
                });

        Uni<List<OntologySearchResponseDTO>> embeddingMatches = ontologyRAG.search(search, maxResults)
                .onItem().transformToUni(results ->
                        Multi.createFrom().iterable(results.entrySet())
                                .onItem().transformToUniAndMerge(entry ->
                                        ontologyDAO.findById(
                                                        UUID.fromString(entry.getKey())
                                                )
                                                .onFailure().recoverWithNull()
                                                .onItem().transform(node -> {
                                                    if (node != null) {
                                                        return new OntologySearchResponseDTO(node, entry.getValue());
                                                    }
                                                    return null;
                                                })
                                )
                                .collect().asList()
                );
        return Uni.combine().all().unis(searchResult, embeddingMatches)
                .with((exact, embed) -> {
                    List<OntologySearchResponseDTO> merged = new ArrayList<>();
                    if (exact != null) {
                        merged.add(exact);
                    }
                    merged.addAll(embed);
                    return merged;
                });
    }

    public Uni<List<OntologyQueryAbilityDTO>> listQueryability(List<String> filterClients) {
        return ontologyDAO.listQueryability()
                .collect().asList();
    }


    public Uni<OntologyDTO> create(CreateOntologyDTO dto) {
        return ontologyDAO.create(dto.getOntology())
                .onItem().transformToUni(createdNode -> createEdgesOfNode(createdNode, dto.getEdges()));
    }

    private Uni<OntologyDTO> createEdgesOfNode(OntologyNodeDTO createdNode, List<OntologyEdgeDTO> edges) {
        if (edges == null || edges.isEmpty()) {
            return Uni.createFrom().item(new OntologyDTO(createdNode));
        }

        return Multi.createFrom().iterable(edges)
                .onItem().transform(edgeDTO -> {
                    // either source or target ID is set; the created node is the other end
                    if (edgeDTO.getSourceId() == null && edgeDTO.getTargetId() != null) {
                        edgeDTO.setSourceId(createdNode.getId().toString());
                    } else if (edgeDTO.getTargetId() == null && edgeDTO.getSourceId() != null) {
                        edgeDTO.setTargetId(createdNode.getId().toString());
                    } else if (edgeDTO.getTargetId() == null && edgeDTO.getSourceId() == null) {
                        throw new IllegalArgumentException(
                                "Either sourceId or targetId must be provided for ontology edge creation."
                        );
                    } else {
                        throw new IllegalArgumentException(
                                "Only one of sourceId or targetId must be provided for ontology edge creation. The other ID is filled with the given ontology."
                        );
                    }
                    return edgeDTO;
                })
                .onItem().transformToUniAndMerge(ontologyDAO::createEdge)
                .collect().asList()
                .onItem().transform(createdEdges -> new OntologyDTO(createdEdges, createdNode));
    }

    public Uni<OntologyNodeDTO> getById(UUID id) {
        return ontologyDAO.findById(id);
    }

    public Uni<List<OntologyNodeDTO>> getByIds(List<UUID> ids) {
        return ontologyDAO.getByIds(ids);
    }

    public Uni<OntologyNodeDTO> getByCUI(String cui) {
        return ontologyDAO.findByCUI(cui);
    }

    public Uni<List<OntologyNodeDTO>> getChildren(UUID id) {
        return ontologyDAO.getChildren(id)
                .collect().asList();
    }

    public Uni<List<OntologyEdgeDTO>> getEdges(UUID id) {
        return ontologyDAO.findAllEdges(id)
                .collect().asList();
    }

    public Uni<OntologyDTO> getNeighbors(UUID id) {
        return ontologyDAO.findAllEdges(id)
                .collect().asList()
                .onItem().transformToUni(edges -> {

                    if (edges.isEmpty()) {
                        return Uni.createFrom().item(new OntologyDTO(List.of(), List.of()));
                    }

                    return Multi.createFrom().iterable(edges)
                            .onItem().transformToUniAndMerge(edge -> {
                                String neighborId = edge.getTargetId();
                                if (neighborId.equals(id.toString())) {
                                    neighborId = edge.getSourceId();
                                }

                                return ontologyDAO.findById(UUID.fromString(neighborId))
                                        .onFailure().recoverWithNull();
                            })
                            .collect().asList()
                            .onItem().transform(nodes -> {

                                List<OntologyNodeDTO> cleanNodes = nodes.stream()
                                        .filter(Objects::nonNull)
                                        .toList();

                                return new OntologyDTO(edges, cleanNodes);
                            });
                });
    }

    public Uni<OntologyNodeDTO> update(UUID id, OntologyNodeDTO dto) {
        return ontologyDAO.update(dto);
    }

    public Uni<Void> delete(UUID id) {
        return ontologyDAO.deleteById(id);
    }
}
