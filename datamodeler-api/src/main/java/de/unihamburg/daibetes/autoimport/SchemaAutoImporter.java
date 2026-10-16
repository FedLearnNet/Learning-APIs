package de.unihamburg.daibetes.autoimport;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import de.unihamburg.daibetes.api.schema.SchemaBO;
import de.unihamburg.daibetes.api.schema.SchemaDAO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@ApplicationScoped
public class SchemaAutoImporter extends BaseAutoImporter<SchemaStructureDTO> {

    private static final String IMPORT_NAME = "Schema";
    private static final String CREATED_BY = "auto-import";

    @Inject
    FLNetConfig flnet;

    @Inject
    SchemaBO schemaBO;

    @Inject
    SchemaDAO schemaDAO;

    @Override
    protected String getImportName() {
        return IMPORT_NAME;
    }

    @Override
    protected FLNetConfigPaths getConfig() {
        return flnet.schemaImports();
    }

    @Override
    protected Class<SchemaStructureDTO> getType() {
        return SchemaStructureDTO.class;
    }

    @Override
    protected String describe(SchemaStructureDTO item) {
        return item == null ? "<empty schema>" : item.getName() + " (" + item.getId() + ")";
    }

    @Override
    protected Uni<Void> importItem(SchemaStructureDTO root, AutoImportContext context, AutoImportCounter counter) {
        if (root == null || root.getId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Schema entry needs a root with an ID"));
        }
        return orNull(schemaDAO.findById(root.getId()))
                .chain(existing -> {
                    if (existing != null) {
                        counter.skipped();
                        return Uni.createFrom().voidItem();
                    }
                    return Multi.createFrom().iterable(flatten(root, context))
                            .onItem().transformToUniAndConcatenate(schemaBO::createWithId)
                            .collect().last()
                            .invoke(ignored -> {
                                counter.created();
                                Log.infof("%s import: created schema %s", IMPORT_NAME, describe(root));
                            })
                            .replaceWithVoid();
                });
    }

    private List<SchemaNodeDTO> flatten(SchemaStructureDTO root, AutoImportContext context) {
        List<SchemaNodeDTO> nodes = new ArrayList<>();
        Deque<SchemaStructureDTO> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            SchemaStructureDTO current = queue.poll();
            nodes.add(toSchemaNode(current, context));
            if (current.getChildren() == null) {
                continue;
            }
            for (SchemaStructureDTO child : current.getChildren()) {
                child.setParentId(current.getId());
                queue.add(child);
            }
        }
        return nodes;
    }

    private SchemaNodeDTO toSchemaNode(SchemaStructureDTO source, AutoImportContext context) {
        if (source.getId() == null) {
            throw new IllegalArgumentException("Schema node without ID: " + source.getName());
        }
        SchemaNodeDTO node = new SchemaNodeDTO();
        node.setId(source.getId());
        node.setName(source.getName());
        node.setDescription(source.getDescription());
        node.setType(source.getType());
        node.setRoot(source.getType() == SchemaNodeType.ROOT);
        node.setParentId(source.getParentId());
        node.setDataTypeId(source.getDataTypeId());
        node.setOntologyId(context.resolveOntology(source.getOntologyId()));
        node.setCreatedBy(CREATED_BY);
        return node;
    }
}
