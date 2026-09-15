package de.unihamburg.daibetes.services.research.semanticscholar;

import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class SemanticScholarServiceImpl {

    @Inject
    @RestClient
    SemanticScholarService semanticScholarService;

    @Inject
    FLNetConfig config;

    public SemanticScholarResponseDTO executeQuery(String query) {
        int configuredLimit = config.tools().semanticScholar().limit();
        int limit = Math.max(0, configuredLimit);

        Optional<String> apiKeyOpt = config.tools().semanticScholar().apiKey();

        SemanticScholarResponseDTO response = (apiKeyOpt.isPresent() && !apiKeyOpt.get().isBlank())
                ? semanticScholarService.search(apiKeyOpt.get(), query, limit)
                : semanticScholarService.search(query, limit);

        if (response != null && response.getData() != null) {
            int toIndex = Math.min(limit, response.getData().size());
            response.setData(response.getData().subList(0, toIndex));
        }

        return response;
    }
}
