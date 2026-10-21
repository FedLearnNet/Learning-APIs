package bio.cosy.feddb.core.autoimport;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface FLNetConfigPaths {

    @WithDefault("false")
    boolean enabled();

    Optional<List<Path>> paths();

    @WithName("whitelist")
    Optional<List<String>> whiteList();
}