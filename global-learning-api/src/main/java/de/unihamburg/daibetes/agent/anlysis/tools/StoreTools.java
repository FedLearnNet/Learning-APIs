package de.unihamburg.daibetes.agent.anlysis.tools;

import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import de.unihamburg.daibetes.agent.store.StoreIngestor;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.store.StoreBO;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class StoreTools {

    @Inject
    StoreBO storeBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    StoreIngestor storeIngestor;

    @Tool("Return all AI tools which the user can use.")
    @Transactional
    public List<String> getTools() {
        return storeBO.list(userIdentity.getKeycloakId(), true)
                .stream()
                .map(this::mapStore)
                .toList();
    }


    private String mapModel(ModelDTO model) {
        return storeIngestor.buildModelDocs(model, model.getFederatedApp()).stream()
                .map(Document::text).collect(Collectors.joining(" "));
    }

    private String mapStore(StoreDTO store) {
        return storeIngestor.toDocuments(store).stream()
                .map(Document::text).collect(Collectors.joining(" "));
    }

}
