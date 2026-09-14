package de.unihamburg.daibetes.api.schema;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class SchemaBO {

    @Inject
    SchemaDAO schemaDAO;

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    SchemaMapper schemaMapper;

    public Uni<PagedResponse<SchemaNodeDTO>> list(int page, int pageSize) {

        Uni<List<SchemaNodeDTO>> items =
                schemaDAO.findAllDetail(page, pageSize)
                        .map(s -> (SchemaNodeDTO) s)
                        .collect()
                        .asList();

        Uni<Long> total =
                schemaDAO.countAll();

        return Uni.combine().all().unis(items, total)
                .asTuple()
                .map(t -> new PagedResponse<>(
                        t.getItem1(),
                        page,
                        pageSize,
                        t.getItem2()
                ));
    }

    public Uni<List<SchemaNodeDetailDTO>> getHead(UUID ontologyId) {
        if (ontologyId == null) {
            return schemaDAO.getAllRootNodes().collect().asList();
        }
        return schemaDAO.getAllHeadByOntologyId(ontologyId)
                .collect()
                .asList();
    }

    public Uni<List<SchemaSubscriptions>> getAllSubscriptions() {
        return schemaDAO.getAllRootNodes()
                .onItem().transformToMulti(root -> {
                    SchemaSubscriptions rootSub = new SchemaSubscriptions(
                            root.getId().toString(),
                            root.getSubscriptions()
                    );
                    if (rootSub.getSubscriptions() == null || rootSub.getSubscriptions().isEmpty()) {
                        return Multi.createFrom().empty();
                    }
                    return schemaDAO.getAllChildrenIds(rootSub.getSchemaId())
                            .onItem().transform(childId -> new SchemaSubscriptions(childId, rootSub.getSubscriptions()));
                })
                .merge()
                .collect().asList();
    }

    private SchemaNodeDTO prePareCreate(SchemaNodeDTO dto) {
        dto.setCreatedAt(LocalDate.now());
        dto.setUpdatedAt(LocalDate.now());
        dto.setVersion(0);
        dto.setId(null);
        return dto;
    }

    public Uni<SchemaNodeDTO> create(SchemaNodeDTO dto) {
        SchemaNodeDTO prepared = prePareCreate(dto);
        return schemaDAO.create(prepared)
                .flatMap(created ->
                {
                    if (dto.getOntologyId() != null && dto.getDataTypeId() != null) {
                        return ontologyDAO.addOntologyToDataTypesEdges(dto.getOntologyId(), List.of(dto.getDataTypeId()))
                                .flatMap(v -> schemaDAO.updateEdges(dto))
                                .flatMap(v -> schemaDAO.findDetail(created.getId()));
                    }
                    return schemaDAO.updateEdges(dto)
                            .flatMap(v -> schemaDAO.findDetail(created.getId()));
                });
    }

    public Uni<SchemaNodeDTO> createHead(SchemaNodeDTO dto) {
        String userId = "test-user"; // TODO: get from security context
        SchemaNodeDTO pre = prePareCreate(dto);
        pre.setRoot(true);
        pre.setCreatedBy(userId);
        return schemaDAO.create(dto);
    }

    public Uni<SchemaNodeDetailDTO> getById(UUID id) {
        return schemaDAO.findDetail(id);
    }

    public Uni<List<SchemaNodeDTO>> getChildren(UUID id) {
        return schemaDAO.getChildren(id)
                .onItem()
                .transform(s -> (SchemaNodeDTO) s)
                .collect()
                .asList();
    }

    public Uni<SchemaNodeDTO> getHeadForSchema(UUID id) {
        return schemaDAO.getRootNode(id)
                .onItem()
                .transform(s -> (SchemaNodeDTO) s);
    }


    public Uni<SchemaStructureDTO> getSubStructure(UUID id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }

        return schemaDAO.findDetail(id)
                .onItem().ifNull().failWith(
                        () -> new NotFoundException("Schema node " + id + " not found")
                )
                .flatMap(this::buildStructureRecursively);
    }


    public Uni<SchemaNodeDTO> update(UUID id, SchemaNodeDTO dto) {
        if (id == null) {
            return Uni.createFrom().failure(
                    new BadRequestException("Schema id must not be null"));
        }

        return schemaDAO.findById(id)
                .flatMap(existing -> {
                    if (existing == null) {
                        return Uni.createFrom().failure(
                                new NotFoundException("SchemaNode " + id + " not found"));
                    }

                    if (!Objects.equals(existing.getVersion(), dto.getVersion())) {
                        return Uni.createFrom().failure(
                                new BadRequestException(
                                        "SchemaNode " + id + " version mismatch. " +
                                                "Current=" + existing.getVersion() +
                                                ", provided=" + dto.getVersion()
                                )
                        );
                    }

                    dto.setUpdatedAt(LocalDate.now());
                    dto.setVersion(dto.getVersion() + 1);

                    return schemaDAO.update(dto)
                            .flatMap(updated -> schemaDAO.updateEdges(dto))
                            .flatMap(v -> schemaDAO.findDetail(id));
                });
    }

    public Uni<Void> delete(UUID id) {
        return schemaDAO.deleteById(id);
    }

    private Uni<SchemaStructureDTO> buildStructureRecursively(SchemaNodeDetailDTO nodeDetail) {
        SchemaStructureDTO structure = schemaMapper.toStructureDTO(nodeDetail);

        return schemaDAO.getChildren(nodeDetail.getId())
                .collect().asList()
                .flatMap(childrenDetails -> {
                    if (childrenDetails.isEmpty()) {
                        structure.setChildren(List.of());
                        return Uni.createFrom().item(structure);
                    }

                    List<Uni<SchemaStructureDTO>> childUnis = childrenDetails.stream()
                            .map(this::buildStructureRecursively)
                            .toList();

                    return Uni.combine().all().unis(childUnis)
                            .with(list -> {
                                List<SchemaStructureDTO> childrenStructs = list.stream()
                                        .map(obj -> (SchemaStructureDTO) obj)
                                        .toList();

                                structure.setChildren(childrenStructs);

                                return structure;
                            });
                });
    }
}
