package de.unihamburg.daibetes.api.schema;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaService;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SchemaServiceImpl implements SchemaService {

    @Inject
    SchemaBO schemaBO;

    @Override
    public Uni<PagedResponse<SchemaNodeDTO>> list(int page, int pageSize) {
        return schemaBO.list(page, pageSize);
    }

    @Override
    public Uni<List<SchemaNodeDetailDTO>> getHead(UUID ontologyId) {
        return schemaBO.getHead(ontologyId);
    }

    @Override
    public Uni<SchemaNodeDTO> create(SchemaNodeDTO dto) {
        return schemaBO.create(dto);
    }

    @Override
    public Uni<SchemaNodeDTO> createHead(SchemaNodeDTO dto) {
        return schemaBO.createHead(dto);
    }

    @Override
    public Uni<SchemaNodeDetailDTO> getById(UUID id) {
        return schemaBO.getById(id);
    }

    @Override
    public Uni<List<SchemaNodeDTO>> getChildren(UUID id) {
        return schemaBO.getChildren(id);
    }

    @Override
    public Uni<SchemaNodeDTO> getHeadForSchema(UUID id) {
        return schemaBO.getHeadForSchema(id);
    }

    @Override
    public Uni<SchemaStructureDTO> getSubStructure(UUID id) {
        return schemaBO.getSubStructure(id);
    }

    @Override
    public Uni<SchemaNodeDTO> update(UUID id, SchemaNodeDTO dto) {
        return schemaBO.update(id, dto);
    }

    @Override
    public Uni<Response> delete(UUID id) {
        return schemaBO.delete(id).onItem().transform(resp -> Response.ok().build());
    }
}
