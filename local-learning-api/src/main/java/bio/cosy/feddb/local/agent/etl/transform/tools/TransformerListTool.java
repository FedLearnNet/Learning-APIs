package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.importer.functions.*;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.*;
import java.util.stream.Stream;

import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics.CODE_MODULE;
import static bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics.TOOLS_MODULE;

@ApplicationScoped
public class TransformerListTool {

    public static final String PATIENT_ID_HYPERPARAM = "patient_id_column";
    public static final String CODE_RETURN_KEY = "value";
    private static final int PAGE_SIZE = 100;

    @Inject
    FunctionManagementBO functionManagementBO;

    @Inject
    @RestClient
    GlobalAPIStoreService storeService;

    @Inject
    CodeTransformerBO codeTransformerBO;

    private List<FunctionsDetailDTO> storeTools;

    @Tool("First choice: list the built-in transformer functions with module, method name, mode (CELL, ROW, PATIENT), parameters and return keys")
    public List<FunctionsDetailDTO> getAllDetail() {
        return functionManagementBO.getAllDetail();
    }

    @Tool("""
            List the store tools (apps running in a container) that transform data, with appVersionId, appImage,
            the input columns they need (parameter column), their output columns (return keys) and hyperparameters.
            Second choice: use a store tool only when no built-in function fits.
            """)
    public synchronized List<FunctionsDetailDTO> getAllStoreDetailed() {
        if (storeTools == null) {
            storeTools = loadStoreTools();
        }
        return storeTools;
    }

    @Tool("""
            List the custom code transformers written and saved earlier (Python, running in a container), with
            mode, the input keys they read (parameters), their return keys and appImage. Third choice: only when
            neither a built-in function nor a store tool fits; reusing one is still better than writing new code.
            """)
    public List<FunctionsDetailDTO> getAllCodeDetailed() {
        return codeTransformers().stream().map(TransformerListTool::toFunction).toList();
    }

    public synchronized void resetStoreTools() {
        storeTools = null;
    }

    public Optional<FunctionsDetailDTO> storeTool(Long appVersionId) {
        return getAllStoreDetailed().stream()
                .filter(tool -> Objects.equals(tool.getAppVersionId(), appVersionId))
                .findFirst();
    }

    public boolean isBuiltInFunction(ConnectorTransformerDTO transformer) {
        return functionManagementBO.getAllDetail().stream()
                .anyMatch(function -> Objects.equals(function.getModuleName(), transformer.getModuleName())
                        && Objects.equals(function.getMethodName(), transformer.getMethodName()));
    }

    public List<String> builtInFunctionNames() {
        return functionManagementBO.getAllDetail().stream().map(FunctionsDetailDTO::getMethodName).toList();
    }

    public boolean resolveAppTransformer(ConnectorTransformerDTO transformer, String patientIdColumn) {
        return transformer.isCodeTransformer()
                ? resolveCodeTransformer(transformer, patientIdColumn)
                : resolveStoreTool(transformer);
    }

    private boolean resolveCodeTransformer(ConnectorTransformerDTO transformer, String patientIdColumn) {
        List<CodeTransformerDTO> ready = codeTransformers();
        Optional<CodeTransformerDTO> code = ready.stream()
                .filter(found -> Objects.equals(found.getImageName(), transformer.getAppImage()))
                .findFirst()
                .or(() -> ready.stream().filter(found -> Objects.equals(found.getName(), transformer.getMethodName())).findFirst());
        code.ifPresent(found -> configure(transformer, found, patientIdColumn));
        return code.isPresent();
    }

    public static void configure(ConnectorTransformerDTO transformer, CodeTransformerDTO code, String patientIdColumn) {
        transformer.setModuleName(CODE_MODULE);
        transformer.setMethodName(code.getName());
        transformer.setAppImage(code.getImageName());
        if (code.getMode() == FunctionExecutionMode.PATIENT && patientIdColumn != null) {
            Map<String, Object> hyperparams = new LinkedHashMap<>(transformer.getHyperparams() == null ? Map.of() : transformer.getHyperparams());
            hyperparams.put(PATIENT_ID_HYPERPARAM, patientIdColumn);
            transformer.setHyperparams(hyperparams);
        }
    }

    private List<CodeTransformerDTO> codeTransformers() {
        return QuarkusTransaction.joiningExisting().call(codeTransformerBO::list).stream()
                .filter(code -> code.getStatus() == CodeTransformerStatus.READY && code.getImageName() != null)
                .toList();
    }

    private static FunctionsDetailDTO toFunction(CodeTransformerDTO code) {
        FunctionsDetailDTO function = new FunctionsDetailDTO();
        function.setModuleName(CODE_MODULE);
        function.setMethodName(code.getName());
        function.setDescription(code.getDescription());
        function.setMode(code.getMode());
        function.setAppImage(code.getImageName());
        if (code.getMode() != FunctionExecutionMode.CELL) {
            function.setParameters(code.getInputKeys() == null ? List.of() : code.getInputKeys().stream()
                    .map(key -> new FunctionParameterDTO(key, FunctionParameterType.STRING, true, null))
                    .toList());
            function.setReturnKeys(code.getReturnKeys() == null || code.getReturnKeys().isEmpty()
                    ? List.of(CODE_RETURN_KEY) : code.getReturnKeys());
        }
        return function;
    }

    public boolean resolveStoreTool(ConnectorTransformerDTO transformer) {
        Optional<FunctionsDetailDTO> tool = storeTool(transformer.getAppVersionId());
        tool.ifPresent(found -> {
            transformer.setModuleName(TOOLS_MODULE);
            transformer.setMethodName(found.getMethodName());
            transformer.setAppImage(found.getAppImage());
        });
        return tool.isPresent();
    }

    private List<FunctionsDetailDTO> loadStoreTools() {
        try {
            List<Long> versionIds = new ArrayList<>();
            int page = 0;
            PagedResponse<StoreDTO> response;
            do {
                response = storeService.list(null, List.of(FederatedAppType.DATA_TRANSFORMATION.name()), null, null,
                        true, true, false, "name:asc", page++, PAGE_SIZE);
                response.getResults().stream()
                        .map(StoreDTO::getApp)
                        .filter(Objects::nonNull)
                        .map(FederatedAppDTO::getLatestVersionId)
                        .filter(Objects::nonNull)
                        .forEach(versionIds::add);
            } while (!response.getResults().isEmpty() && (long) page * PAGE_SIZE < response.getTotalCount());
            List<FunctionsDetailDTO> tools = versionIds.isEmpty()
                    ? List.of()
                    : storeService.getAppsByVersions(versionIds).stream().map(this::toFunction).toList();
            Log.infof("Loaded %d store tools for the transformer selection", tools.size());
            return tools;
        } catch (RuntimeException e) {
            Log.warnf("Could not load the store tools for the transformer selection: %s", e.getMessage());
            return List.of();
        }
    }

    private FunctionsDetailDTO toFunction(FederatedAppDetailDTO app) {
        FunctionsDetailDTO tool = new FunctionsDetailDTO();
        tool.setModuleName(TOOLS_MODULE);
        tool.setMethodName(app.getName());
        tool.setDescription(app.getShortDescription());
        tool.setAppVersionId(app.getLatestVersionId());
        tool.setAppImage(app.getImageName());
        List<FunctionParameterDTO> parameters = new ArrayList<>();
        List<String> inputColumns = app.getAppConfig() == null ? List.of() : requiredColumns(app.getAppConfig().getInput().stream());
        parameters.add(new FunctionParameterDTO("column", FunctionParameterType.STRING, !inputColumns.isEmpty(), null,
                "Comma separated source columns passed to the tool, it expects the columns " + inputColumns,
                null, FunctionParameterUsage.INPUT));
        if (app.getAppConfig() != null) {
            app.getAppConfig().getHyperparams().forEach(hyperparam -> parameters.add(toParameter(hyperparam)));
            tool.setReturnKeys(requiredColumns(app.getAppConfig().getOutput().stream()));
        }
        tool.setParameters(parameters);
        return tool;
    }

    private static FunctionParameterDTO toParameter(ToolHyperParamConfigDTO hyperparam) {
        String name = hyperparam.getVariableName() != null ? hyperparam.getVariableName() : hyperparam.getName();
        FunctionParameterType type = hyperparam.getType() == null ? FunctionParameterType.STRING : switch (hyperparam.getType()) {
            case INTEGER -> FunctionParameterType.INTEGER;
            case FLOAT -> FunctionParameterType.DOUBLE;
            case BOOLEAN -> FunctionParameterType.BOOLEAN;
            case STRING, CATEGORICAL -> FunctionParameterType.STRING;
        };
        return new FunctionParameterDTO(name, type, hyperparam.getDefaultValue() == null, hyperparam.getDefaultValue(),
                hyperparam.getDescription(), hyperparam.getOptions(), FunctionParameterUsage.HYPERPARAMETER);
    }

    private static List<String> requiredColumns(Stream<? extends ToolConfigDTO> configs) {
        return configs
                .filter(config -> config.getTabularSchema() != null && config.getTabularSchema().getRequiredColumns() != null)
                .flatMap(config -> config.getTabularSchema().getRequiredColumns().stream())
                .distinct()
                .toList();
    }
}
