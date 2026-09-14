package de.unihamburg.daibetes.api.ontology;

import bio.cosy.feddb.core.api.datamodler.ontology.*;
import bio.cosy.feddb.core.base.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OntologyServiceImpl implements OntologyService {

    @Inject
    OntologyBO ontologyBO;

    @Override
    public Uni<PagedResponse<OntologyNodeDTO>> list(String search, int page, int pageSize) {
        return ontologyBO.list(search, page, pageSize);
    }

    @Override
    public Uni<List<OntologySearchResponseDTO>> searchEmbedding(String search, int maxResults) {
        return ontologyBO.searchEmbedding(search, maxResults);
    }

    @Override
    public Uni<List<OntologyQueryAbilityDTO>> listQueryability(List<String> filterClients) {
        return ontologyBO.listQueryability(filterClients);
    }

    @Override
    public Uni<OntologyDTO> create(CreateOntologyDTO dto) {
        return ontologyBO.create(dto);
    }

    @Override
    public Uni<OntologyNodeDTO> getById(UUID id) {
        return ontologyBO.getById(id);
    }

    @Override
    public Uni<List<OntologyNodeDTO>> getByIds(List<UUID> ids) {
        return ontologyBO.getByIds(ids);
    }

    @Override
    public Uni<OntologyNodeDTO> getViaCuiId(String cui) {
        return ontologyBO.getByCUI(cui);
    }

    @Override
    public Uni<List<OntologyNodeDTO>> getChildren(UUID id) {
        return ontologyBO.getChildren(id);
    }

    @Override
    public Uni<List<OntologyEdgeDTO>> getEdges(UUID id) {
        return ontologyBO.getEdges(id);
    }

    @Override
    public Uni<OntologyDTO> getNeighbors(UUID id) {
        return ontologyBO.getNeighbors(id);
    }

    @Override
    public Uni<OntologyNodeDTO> update(UUID id, OntologyNodeDTO dto) {
        return ontologyBO.update(id, dto);
    }

    @Override
    public Uni<Response> delete(UUID id) {
        return ontologyBO.delete(id).onItem().transform(resp -> Response.ok().build());
    }
}
