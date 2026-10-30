package de.unihamburg.daibetes.api.umls;

import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.feature.FeatureAvailableBO;
import de.unihamburg.daibetes.api.feature.FeatureAvailableMessages;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import de.unihamburg.daibetes.api.umls.importer.ImportStatus;
import de.unihamburg.daibetes.api.umls.importer.UmlsImportBO;
import de.unihamburg.daibetes.api.umls.search.*;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class UmlsServiceImpl implements UmlsService {
    @Inject
    UmlsImportBO umlsImportBO;

    @Inject
    UMLSSearchBO umlsSearchBO;

    @Inject
    OntologyRAG ontologyRAG;

    @Inject
    FeatureAvailableBO featureAvailableBO;

    @Override
    public Uni<Response> create(boolean createAlsoEmbedding) {
        try {
            if (!featureAvailableBO.isEmbeddingEnabled()) {
                createAlsoEmbedding = false;
            }
            umlsImportBO.startImport(createAlsoEmbedding);
            return Uni.createFrom().item(Response.ok().build());
        } catch (BadRequestException e) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
        } catch (InternalServerErrorException e) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
        }
    }

    @Override
    public Uni<Response> createAllEmbedding() {
        if (!featureAvailableBO.isEmbeddingEnabled()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity(FeatureAvailableMessages.EMBEDDING_DISABLED)
                    .build());
        }
        return ontologyRAG.ingestAllNodes()
                .onItem().transform(v -> Response.ok().build())
                .onFailure().recoverWithItem(e -> {
                    Log.error("Failed to ingest all nodes", e);
                    String message = e.getMessage();
                    if (message == null) {
                        message = "Failed to ingest all nodes";
                    }
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(message)
                            .build();
                });
    }

    @Override
    public Uni<PagedResponse<UMLSSearchResultDTO>> search(String searchString, int page, int pageSize, UMLSSources sources) {
        if (!featureAvailableBO.isUmlsSearchEnabled()) {
            throw new NotAllowedException(FeatureAvailableMessages.UMLS_SEARCH_DISABLED);
        }
        if (searchString == null || searchString.isEmpty()) {
            return Uni.createFrom().item(new PagedResponse<>(List.of(), page, pageSize, 0));
        }
        return umlsSearchBO.search(searchString, page, pageSize, sources);
    }

    @Override
    public Uni<UMLSDetailResultDTO> getItem(String id, UMLSSources source) {
        return umlsSearchBO.getItem(id, source);
    }

    @Override
    public Uni<List<UMLSCytoscapeGraphElementDTO>> createCytoscapeGraph(String id, UMLSSources source) {
        if (!featureAvailableBO.isUmlsSearchEnabled()) {
            throw new NotAllowedException(FeatureAvailableMessages.UMLS_SEARCH_DISABLED);
        }
        return umlsSearchBO.createCytoscapeGraph(id, source);
    }

    @Override
    public Uni<String> createAllParentsGraph(UMLSIdSourceDTO body) {
        return umlsImportBO.createAllParentsGraph(body);
    }

    @Override
    public ImportStatus getImportStatus() {
        return umlsImportBO.getStatus();
    }
}
