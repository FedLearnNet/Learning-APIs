package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.local.api.importer.functions.FunctionManagementBO;
import bio.cosy.feddb.local.api.importer.functions.FunctionsDetailDTO;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class TransformerListTool {

    @Inject
    FunctionManagementBO functionManagementBO;

    @Tool("List all transformer functions with module, method name, mode (CELL, ROW, PATIENT), parameters and return keys")
    public List<FunctionsDetailDTO> getAllDetail() {
        return functionManagementBO.getAllDetail();
    }
}
