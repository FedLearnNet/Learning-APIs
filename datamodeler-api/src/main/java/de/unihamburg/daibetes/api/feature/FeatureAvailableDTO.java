package de.unihamburg.daibetes.api.feature;

import lombok.Data;

@Data
public class FeatureAvailableDTO {

    private boolean umlsSearchEnabled = false;
    private boolean embeddingEnabled = false;
}
