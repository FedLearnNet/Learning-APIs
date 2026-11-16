package de.unihamburg.daibetes.config;

import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

//Intended for all custom properties with a "flnet" prefix
@ConfigMapping(prefix = "flnet")
public interface FLNetConfig {

    @WithName("ontology-imports")
    OntologyImportConfig ontologyImports();

    @WithName("datatype-imports")
    FLNetConfigPaths datatypeImports();

    @WithName("schema-imports")
    FLNetConfigPaths schemaImports();

    interface OntologyImportConfig extends FLNetConfigPaths {

        // SapBERT embeddings for imported ontologies that have none yet, created after all imports
        @WithDefault("false")
        boolean createEmbeddings();
    }
}
