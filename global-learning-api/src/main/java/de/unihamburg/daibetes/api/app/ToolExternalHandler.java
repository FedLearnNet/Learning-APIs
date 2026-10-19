package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.autoimport.AutoImportPathHandler;
import bio.cosy.feddb.core.autoimport.OnParseError;
import bio.cosy.feddb.core.autoimport.PathOrUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.helper.ToolExternalCleaner;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class ToolExternalHandler {

    private static final String IMPORT_NAME = "Tool";

    @Inject
    FLNetConfig flnet;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AutoImportPathHandler autoImportPathHandler;

    @Inject
    FederatedAppCreateImportBO federatedAppCreateImportBO;

    @Inject
    ModelBO modelBO;

    @Transactional
    public void importAllToolsTransactional() {
        List<PathOrUrl> jsonSources = autoImportPathHandler.resolveSources(IMPORT_NAME, flnet.toolImports());
        if (jsonSources.isEmpty()) {
            return;
        }

        List<FederatedAppDetailDTO> tools = autoImportPathHandler.readAll(IMPORT_NAME, jsonSources, FederatedAppDetailDTO.class);
        for (FederatedAppDetailDTO tool : tools) {
            try {
                federatedAppCreateImportBO.createExternal(tool);
            } catch (Exception e) {
                Log.warnf(e, "Failed to import tool from source: %s", tool.getName());
            }
        }

        List<ModelDetailDTO> models = autoImportPathHandler.readAll(IMPORT_NAME, jsonSources, ModelDetailDTO.class, OnParseError.FAIL);
        for (ModelDetailDTO model : models) {
            if (model.getFederatedApp() == null) {
                // is Tool not an app;
                continue;
            }
            try {
                federatedAppCreateImportBO.createExternal(model.getFederatedApp());
                modelBO.createExternal(model);
            } catch (Exception e) {
                Log.warnf(e, "Failed to import model from source: %s", model.getName());
            }
        }
    }

    public Optional<Path> saveToolAsJson(FederatedAppDetailDTO tool) {
        if (tool == null) {
            Log.warn("saveToolAsJson called with null tool");
            return Optional.empty();
        }

        ToolExternalCleaner.cleanup(tool);
        ObjectNode root = autoImportPathHandler.toJsonTree(tool);
        makeImageReferencesPortable(root);
        return autoImportPathHandler.writeJson(IMPORT_NAME, flnet.toolImports(), fileName(tool), root);
    }

    public Optional<Path> replaceBuildInfoInJson(FederatedAppDetailDTO tool) {
        Optional<Path> outFile = autoImportPathHandler.resolveWritableRoot(IMPORT_NAME, flnet.toolImports())
                .map(root -> root.resolve(fileName(tool)))
                .filter(Files::exists);
        if (outFile.isEmpty()) {
            return saveToolAsJson(tool);
        }

        try {
            JsonNode root = objectMapper.readTree(outFile.get().toFile());
            JsonNode version = StreamSupport.stream(root.path("versions").spliterator(), false)
                    .filter(value -> Objects.equals(value.path("appVersion").asText(), tool.getLatestVersion()))
                    .findFirst().orElse(root.path("versions").path(0));
            if (!(version instanceof ObjectNode target) || tool.getPublishInfo() == null || tool.getImageName() == null)
                return Optional.empty();
            target.set("publishInfo", objectMapper.valueToTree(tool.getPublishInfo()));
            target.put("imageName", tool.getImageName());
            makeImageReferencesPortable((ObjectNode) root);
            return autoImportPathHandler.writeJson(outFile.get(), root);
        } catch (Exception e) {
            Log.warnf(e, "Failed to replace build information in %s", outFile.get());
            return Optional.empty();
        }
    }

    private void makeImageReferencesPortable(ObjectNode tool) {
        String slug = tool.path("slug").asText(null);
        if (StringUtils.isBlank(slug)) {
            return;
        }
        for (JsonNode version : tool.path("versions")) {
            if (!(version instanceof ObjectNode target)
                    || !(version.path("publishInfo") instanceof ObjectNode publishInfo)) {
                continue;
            }
            String commitHash = publishInfo.path("commitHash").asText(null);
            if (StringUtils.isBlank(commitHash)) {
                continue;
            }
            String imageName = slug + ":" + commitHash.toLowerCase(Locale.ROOT);
            target.put("imageName", imageName);
            publishInfo.put("imageName", imageName);
        }
    }

    private String fileName(FederatedAppDetailDTO tool) {
        return safeSlug(tool) + ".json";
    }

    private String safeSlug(FederatedAppDetailDTO dto) {
        if (dto == null) return null;
        String fallback = "tool_" + (dto.getUniqueAppId() != null ? dto.getUniqueAppId() : Math.abs(Objects.hash(dto)));
        if (dto.getSlug() == null) return fallback;
        String s = dto.getSlug().trim().toLowerCase(Locale.ROOT);
        return s.isBlank() ? fallback : s;
    }

}
