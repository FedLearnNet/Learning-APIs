package de.unihamburg.daibetes.agent.anlysis.tools;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.agent.store.StoreIngestor;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.store.StoreBO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.data.document.Document;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlanNextStepTools {

    @Inject
    StoreBO storeBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    StoreIngestor storeIngestor;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Inject
    DataAnalysisChatMessageBO messageBO;

    public List<String> listUsableStore() {
        return storeBO.list(userIdentity.getKeycloakId(), true).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(s -> {
                    if (s.getApp() != null && s.getApp().getName() != null) return s.getApp().getName();
                    if (s.getModel() != null && s.getModel().getName() != null) return s.getModel().getName();
                    return "";
                }))
                .map(this::storeAsSearchText)
                .toList();
    }

    @Tool("""
            Search Store elements by a free-text query (capability, tag, input/output, hyperparams).
            This is NOT embedding search; it's a safe lexical filter over StoreBO.list(...).
            Return up to 'limit' compact lines.
            """)
    @Transactional
    public List<String> searchStoreLexical(@P("query to find") String query, @P("how many return, default 5") Integer limit, @ToolMemoryId String sessionId) {
        ToolDTO toolDTO = new ToolDTO("Plan next step - searchStoreLexical");
        toolDTO.setInput(query);
        messageBO.sendTool(toolDTO, sessionId);

        if (limit == null || limit <= 0) {
            limit = 5;
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isBlank()) {
            return listUsableStore().stream().limit(limit).toList();
        }

        List<String> result = storeBO.list(userIdentity.getKeycloakId(), true).stream()
                .filter(Objects::nonNull)
                .limit(limit)
                .map(this::storeAsSearchText)
                .toList();
        toolDTO.addContent(result);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }

    @Tool("""
               Fetch StoreDTO for a Store's latest app/model version by storeId.
                If not found or not accessible, return null.
            """)
    public StoreDTO getStoreById(@P("Store id, can be the pk of app or model") Long storeId, @P("Store kind, APP or MODEL") String kind, @ToolMemoryId String sessionId) {
        if (kind == null || (!kind.equalsIgnoreCase("app") && !kind.equalsIgnoreCase("model"))) {
            kind = null;
        }
        if (storeId == null) return null;

        ToolDTO toolDTO = new ToolDTO("Plan next step - getStoreById");
        toolDTO.setInput(storeId + " Kind: " + kind);
        messageBO.sendTool(toolDTO, sessionId);
        // If StoreBO has a direct find-by-id, use it. Otherwise safe fallback: list+filter.
        String finalKind = kind;
        StoreDTO result = storeBO.list(userIdentity.getKeycloakId(), true).stream()
                .filter(Objects::nonNull)
                .filter(s -> {
                    if (finalKind != null) {
                        if (finalKind.equalsIgnoreCase("app") && s.getApp() != null && s.getApp().getId().equals(storeId)) {
                            return true;
                        }
                        if (finalKind.equalsIgnoreCase("model") && s.getModel() != null && s.getModel().getId().equals(storeId)) {
                            return true;
                        }
                    }
                    return storeId.equals(s.getId());
                })
                .findFirst()
                .orElse(null);

        String resultString = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            resultString = mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            resultString = "Error serializing StoreDTO";
        }
        toolDTO.addContent(resultString);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }

    @Tool("""
            Fetch hyperparams (default values) for a Store's latest app/model version by storeId.
            If not found or not accessible, return null.
            """)
    @Transactional
    public String getHyperparams(@P("Store id, can be the pk of app or model") Long storeId, @P("Store kind, APP or MODEL") String kind, @ToolMemoryId String sessionId) {
        FederatedAppVersionDTO appVersion = getVersion(storeId, kind, sessionId);
        if (appVersion == null) {
            return null;
        }

        ToolDTO toolDTO = new ToolDTO("Plan next step - getHyperparams");
        toolDTO.setInput(storeId + " Kind: " + kind);
        messageBO.sendTool(toolDTO, sessionId);
        String result = Optional.ofNullable(appVersion.getAppConfig().getHyperparams()).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                ToolHyperParamConfigDTO::getVariableName,
                                ToolHyperParamConfigDTO::getDefaultValue,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ),
                        map -> {
                            try {
                                return new ObjectMapper().writeValueAsString(map);
                            } catch (JsonProcessingException e) {
                                return null;
                            }
                        }
                ));

        toolDTO.addContent(result);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }

    @Tool("""
            Fetch hyperparams (default values) for a Store's latest app/model version by storeId.
            If not found or not accessible, return null.
            """)
    @Transactional
    public String getInputs(@P("Store id, can be the pk of app or model") Long storeId, @P("Store kind, APP or MODEL") String kind, @ToolMemoryId String sessionId) {
        FederatedAppVersionDTO appVersion = getVersion(storeId, kind, sessionId);
        if (appVersion == null) {
            return null;
        }
        ToolDTO toolDTO = new ToolDTO("Plan next step - getInputs");
        toolDTO.setInput(storeId + " Kind: " + kind);
        messageBO.sendTool(toolDTO, sessionId);
        String result = Optional.ofNullable(appVersion.getAppConfig().getInput()).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                ToolInputConfigDTO::getVariableName,
                                this::inputAsString,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ),
                        map -> {
                            try {
                                return new ObjectMapper().writeValueAsString(map);
                            } catch (JsonProcessingException e) {
                                return null;
                            }
                        }
                ));

        toolDTO.addContent(result);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }


    @Tool("""
            Return a compact "capability doc" text for a storeId (derived from the ingested documents).
            Use this when you need to compare two candidates quickly.
            """)
    @Transactional
    public String getStoreCapabilityDoc(@P("Store id, can be the pk of app or model") Long storeId, @P("Store kind, APP or MODEL") String kind, @ToolMemoryId String sessionId) {
        StoreDTO dto = getStoreById(storeId, kind, sessionId);
        ToolDTO toolDTO = new ToolDTO("Plan next step - getStoreCapabilityDoc");
        toolDTO.setInput(storeId + " Kind: " + kind);
        messageBO.sendTool(toolDTO, sessionId);
        String result = storeAsSearchText(dto);
        toolDTO.addContent(result);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }


    private FederatedAppVersionDTO getVersion(Long storeId, String kind, String sessionId) {
        StoreDTO store = getStoreById(storeId, kind, sessionId);
        Long appVersionId = null;
        if (store != null && store.getApp() != null) {
            appVersionId = store.getApp().getLatestVersionId();
        } else if (store != null && store.getModel() != null) {
            appVersionId = store.getModel().getFederatedAppVersionId();
        }
        if (appVersionId == null) {
            return null;
        }
        return federatedAppVersionBO.getById(appVersionId);
    }

    private String inputAsString(ToolInputConfigDTO input) {
        return input.getType() + " (" + input.getDescription() + ")";
    }

    private String storeAsSearchText(StoreDTO dto) {
        if (dto == null) {
            return "Not found";
        }
        return storeIngestor.toDocuments(dto).stream()
                .map(Document::text)
                .collect(Collectors.joining("\n"));
    }
}
