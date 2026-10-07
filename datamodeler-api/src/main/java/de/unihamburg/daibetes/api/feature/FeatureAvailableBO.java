package de.unihamburg.daibetes.api.feature;

import de.unihamburg.daibetes.embedding.SapBERTEmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import de.unihamburg.daibetes.api.config.UMLSConfig;


@ApplicationScoped
public class FeatureAvailableBO {

    @Inject
    UMLSConfig umlsConfig;

    @Inject
    SapBERTEmbeddingModel sapBERTEmbeddingModel;


    public boolean isUmlsSearchEnabled() {
        return umlsConfig.api().key()
                .filter(key -> !key.isBlank())
                .filter(key -> !key.equalsIgnoreCase("VIA_ENV"))
                .isPresent();
    }

    public boolean isEmbeddingEnabled() {
        return sapBERTEmbeddingModel.enabled();
    }

    public FeatureAvailableDTO get() {
        FeatureAvailableDTO dto = new FeatureAvailableDTO();
        dto.setUmlsSearchEnabled(isUmlsSearchEnabled());
        dto.setEmbeddingEnabled(isEmbeddingEnabled());
        return dto;
    }

}
