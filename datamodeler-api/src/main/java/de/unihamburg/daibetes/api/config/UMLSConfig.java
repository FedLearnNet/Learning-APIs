package de.unihamburg.daibetes.api.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;


@ConfigMapping(prefix = "umls")
public interface UMLSConfig {

    Language language();

    Sab sab();

    Api api();

    @WithName("import")
    Import importConfig();

    AutoImport autoImport();

    interface Language {
        @WithDefault("eng")
        List<String> filter();
    }

    interface Sab {
        @WithDefault("RXNORM,LNC,NCBI,ICD9CM,ICD10PCS")
        List<String> filter();
    }

    interface Api {
        Optional<String> key();
    }

    interface Import {
        @WithDefault("100")
        int batchSize();

        @WithDefault("2000")
        int sizeMax();

        @WithDefault("2000")
        int edgeSizeMax();

        @WithDefault("false")
        boolean allowSelfLoops();

        Optional<String> folderPath();

        Optional<String> mrconso();

        Optional<String> mrrel();

        Sab sab();
    }

    interface AutoImport {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("3600")
        int timeoutSeconds();
    }
}
