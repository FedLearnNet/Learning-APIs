package de.unihamburg.daibetes.autoimport;

import bio.cosy.feddb.core.api.datamodler.ontology.CreateOntologyDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import de.unihamburg.daibetes.api.ontology.OntologyBO;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@ApplicationScoped
public class OntologyAutoImporter extends BaseAutoImporter<CreateOntologyDTO> {

    private static final String IMPORT_NAME = "Ontology";

    @Inject
    FLNetConfig flnet;

    @Inject
    OntologyBO ontologyBO;

    @Inject
    OntologyDAO ontologyDAO;

    @Override
    protected String getImportName() {
        return IMPORT_NAME;
    }

    @Override
    protected FLNetConfigPaths getConfig() {
        return flnet.ontologyImports();
    }

    @Override
    protected Class<CreateOntologyDTO> getType() {
        return CreateOntologyDTO.class;
    }

    @Override
    protected String describe(CreateOntologyDTO item) {
        if (item == null || item.getOntology() == null) {
            return "<empty ontology>";
        }
        OntologyNodeDTO node = item.getOntology();
        return StringUtils.defaultIfBlank(node.getCui(), String.valueOf(node.getNames())) + " (" + node.getId() + ")";
    }

    @Override
    protected Uni<Void> importItem(CreateOntologyDTO item, AutoImportContext context, AutoImportCounter counter) {
        if (item == null || item.getOntology() == null || item.getOntology().getId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Ontology entry needs an ontology with an ID"));
        }
        OntologyNodeDTO node = item.getOntology();
        return findExisting(node)
                .chain(existing -> {
                    if (existing != null) {
                        counter.skipped();
                        return Uni.createFrom().item(existing.getId());
                    }
                    remapEdges(item.getEdges(), context);
                    return ontologyBO.create(item)
                            .invoke(ignored -> counter.created())
                            .map(created -> created.getNodes().getFirst().getId());
                })
                .invoke(actualId -> context.mapOntology(node.getId(), actualId))
                .replaceWithVoid();
    }

    private Uni<OntologyNodeDTO> findExisting(OntologyNodeDTO node) {
        if (StringUtils.isNotBlank(node.getCui())) {
            return orNull(ontologyDAO.findByCUI(node.getCui()));
        }
        return orNull(ontologyDAO.findById(node.getId()));
    }

    private void remapEdges(List<OntologyEdgeDTO> edges, AutoImportContext context) {
        if (edges == null) {
            return;
        }
        for (OntologyEdgeDTO edge : edges) {
            edge.setSourceId(context.resolveOntology(edge.getSourceId()));
            edge.setTargetId(context.resolveOntology(edge.getTargetId()));
        }
    }
}
