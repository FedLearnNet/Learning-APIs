package de.unihamburg.daibetes.api.app.config;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.autoimport.AutoImportPathHandler;
import bio.cosy.feddb.core.autoimport.OnParseError;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ToolConfigExternalStandardHandler {

    private static final String IMPORT_NAME = "Tool config";

    @Inject
    FLNetConfig flnet;

    @Inject
    AutoImportPathHandler autoImportPathHandler;

    public List<ToolConfigDTO> loadAll() {
        return autoImportPathHandler.loadAll(IMPORT_NAME, flnet.toolConfigs(), ToolConfigDTO.class, OnParseError.FAIL);
    }
}
