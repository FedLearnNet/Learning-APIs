package bio.cosy.feddb.transform.tools;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.*;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.FunctionsDetailDTO;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransformerListToolTest {

    @Mock
    GlobalAPIStoreService storeService;

    @InjectMocks
    TransformerListTool tool;

    @BeforeEach
    void setUp() {
        FederatedAppDTO app = new FederatedAppDTO();
        app.setLatestVersionId(7L);
        StoreDTO entry = new StoreDTO();
        entry.setApp(app);
        PagedResponse<StoreDTO> page = new PagedResponse<>();
        page.setResults(List.of(entry));
        page.setTotalCount(1);
        when(storeService.list(any(), anyList(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), anyInt(), anyInt()))
                .thenReturn(page);
        when(storeService.getAppsByVersions(List.of(7L))).thenReturn(List.of(icdGrouper()));
    }

    @Test
    void describesStoreToolsLikeFunctions() {
        FunctionsDetailDTO grouper = tool.getAllStoreDetailed().getFirst();

        assertEquals(TransformerListTool.TOOLS_MODULE, grouper.getModuleName());
        assertEquals("ICD Grouper", grouper.getMethodName());
        assertEquals(7L, grouper.getAppVersionId());
        assertEquals("registry/icd-grouper:1.0", grouper.getAppImage());
        assertEquals(List.of("icd_group"), grouper.getReturnKeys());
        FunctionParameterDTO column = grouper.getParameters().getFirst();
        assertEquals("column", column.getName());
        assertTrue(column.getDescription().contains("diagnosis"));
        FunctionParameterDTO level = grouper.getParameters().get(1);
        assertEquals("level", level.getName());
        assertEquals(FunctionParameterUsage.HYPERPARAMETER, level.getUsage());
        assertEquals("3", level.getDefaultValue());
    }

    @Test
    void loadsTheStoreToolsOncePerRun() {
        tool.getAllStoreDetailed();
        tool.storeTool(7L);
        verify(storeService, times(1)).getAppsByVersions(anyList());

        tool.resetStoreTools();
        tool.getAllStoreDetailed();
        verify(storeService, times(2)).getAppsByVersions(anyList());
    }

    private static FederatedAppDetailDTO icdGrouper() {
        FederatedAppDetailDTO app = new FederatedAppDetailDTO();
        app.setName("ICD Grouper");
        app.setShortDescription("Groups ICD codes into chapters");
        app.setLatestVersionId(7L);
        app.setImageName("registry/icd-grouper:1.0");
        ToolConfigsDTO config = new ToolConfigsDTO();
        ToolInputConfigDTO input = new ToolInputConfigDTO();
        input.setTabularSchema(schema("diagnosis"));
        ToolOutputConfigDTO output = new ToolOutputConfigDTO();
        output.setTabularSchema(schema("icd_group"));
        ToolHyperParamConfigDTO level = new ToolHyperParamConfigDTO();
        level.setName("Level");
        level.setVariableName("level");
        level.setType(ToolConfigHyperParamDataType.INTEGER);
        level.setDefaultValue("3");
        config.setInput(List.of(input));
        config.setOutput(List.of(output));
        config.setHyperparams(List.of(level));
        app.setAppConfig(config);
        return app;
    }

    private static TabularSchemaDTO schema(String column) {
        TabularSchemaDTO schema = new TabularSchemaDTO();
        schema.setRequiredColumns(List.of(column));
        return schema;
    }
}
