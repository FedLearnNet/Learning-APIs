package bio.cosy.feddb.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.agent.etl.transform.model.SavedCodeTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRequestBO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransformerWriterToolTest {

    private static final String BODY = "return row['first'] + row['second']";

    private final DataTypeNodeDTO goal = new DataTypeNodeDTO();

    @Mock
    CodeTransformerContainerBO containerBO;

    @Mock
    CodeTransformerRequestBO requestBO;

    @Mock
    CodeTransformerBO codeTransformerBO;

    @InjectMocks
    TransformerWriterTool tool;

    @BeforeEach
    void setUp() {
        goal.setType(DataTypes.STRING);
        LocalSchemaNodeDTO node = new LocalSchemaNodeDTO();
        node.setId(7L);
        node.setName("full_name");
        TableSample sample = new TableSample(List.of("patient", "first", "second"), List.of(
                Map.of("patient", "p1", "first", "Ada", "second", "Lovelace"),
                Map.of("patient", "p2", "first", "Alan", "second", "Turing"),
                Map.of("patient", "p2", "first", "Grace", "second", "Hopper")), null);
        tool.init(new TransformerRunContext(null, "patient", "user", Map.of(), new HashMap<>(), new ArrayList<>(), null), node, goal, sample);
        // Checks succeed unless a test says otherwise; rejected input never reaches the runner
        lenient().when(requestBO.check(any(), anyString())).thenReturn(result(true));
    }

    @Test
    void checksTheCodeOnThePreviewValuesOfTheInputColumns() {
        CodeTransformerCheckResultDTO result = tool.checkCode(FunctionExecutionMode.ROW, List.of("first", "second"), BODY);

        assertTrue(result.getIsSuccess());
        verify(containerBO).start("user");
        CodeTransformerCheckDTO request = sentCheck();
        assertEquals(BODY, request.getBody());
        assertEquals(goal, request.getGoal());
        assertEquals(Map.of("first", List.of("Ada", "Alan", "Grace"), "second", List.of("Lovelace", "Turing", "Hopper")),
                request.getData());
    }

    @Test
    void checksPatientCodeOnThePatientWithTheMostRows() {
        tool.checkCode(FunctionExecutionMode.PATIENT, List.of("first"), "return [None for row in rows]");

        assertEquals(Map.of("first", List.of("Alan", "Grace")), sentCheck().getData());
    }

    @Test
    void rejectsUnknownColumnsWithoutStartingTheRunner() {
        CodeTransformerCheckResultDTO result = tool.checkCode(FunctionExecutionMode.CELL, List.of("last"), "return value");

        assertFalse(result.getIsSuccess());
        assertTrue(result.getMessage().startsWith("Unknown columns [last]"), result.getMessage());
        verify(containerBO, never()).start(any());
    }

    @Test
    void savesOnlyCheckedCode() {
        when(requestBO.check(any(), anyString())).thenReturn(result(false));
        tool.checkCode(FunctionExecutionMode.ROW, List.of("first", "second"), BODY);

        SavedCodeTransformer saved = tool.saveCode("Join names", "");

        assertFalse(saved.isSaved());
        verify(codeTransformerBO, never()).save(any(), any());
    }

    @Test
    void savedRowCodeReadsTheCheckedColumnsAndWritesANewColumn() {
        when(codeTransformerBO.save(any(), anyString())).thenReturn(code(FunctionExecutionMode.ROW));
        tool.checkCode(FunctionExecutionMode.ROW, List.of("first", "second"), BODY);

        SavedCodeTransformer saved = tool.saveCode("Join names", "Joins first and last name");

        assertTrue(saved.isSaved(), saved.getMessage());
        ConnectorTransformerDTO transformer = saved.getTransformer();
        assertEquals(TransformerListTool.CODE_MODULE, transformer.getModuleName());
        assertEquals("Join names", transformer.getMethodName());
        assertEquals("fl-net/code/transformer-3:abc", transformer.getAppImage());
        assertEquals(Map.of("first", "first", "second", "second"), transformer.getInputMapping());
        assertEquals(Map.of(TransformerListTool.CODE_RETURN_KEY, "full_name"), transformer.getReturnMapping());
        assertEquals("full_name", saved.getMappedColumn());
        ArgumentCaptor<CodeTransformerSaveDTO> request = ArgumentCaptor.forClass(CodeTransformerSaveDTO.class);
        verify(codeTransformerBO).save(request.capture(), anyString());
        assertEquals(BODY, request.getValue().getBody());
    }

    @Test
    void savedCellCodeRewritesItsColumn() {
        when(codeTransformerBO.save(any(), anyString())).thenReturn(code(FunctionExecutionMode.CELL));
        tool.checkCode(FunctionExecutionMode.CELL, List.of("first"), "return value.upper()");

        SavedCodeTransformer saved = tool.saveCode("Upper case", "");

        assertEquals("first", saved.getTransformer().getColumn());
        assertEquals("first", saved.getMappedColumn());
    }

    @Test
    void savingAgainUpdatesTheSameTransformerAndDiscardDeletesIt() {
        when(codeTransformerBO.save(any(), anyString())).thenReturn(code(FunctionExecutionMode.ROW));
        tool.checkCode(FunctionExecutionMode.ROW, List.of("first", "second"), BODY);
        tool.saveCode("Join names", "");
        tool.saveCode("Join names", "");

        tool.discard();

        ArgumentCaptor<CodeTransformerSaveDTO> requests = ArgumentCaptor.forClass(CodeTransformerSaveDTO.class);
        verify(codeTransformerBO, times(2)).save(requests.capture(), anyString());
        assertNull(requests.getAllValues().get(0).getId());
        assertEquals(3L, requests.getAllValues().get(1).getId());
        verify(codeTransformerBO).delete(3L);
    }

    private CodeTransformerCheckDTO sentCheck() {
        ArgumentCaptor<CodeTransformerCheckDTO> request = ArgumentCaptor.forClass(CodeTransformerCheckDTO.class);
        verify(requestBO).check(request.capture(), anyString());
        return request.getValue();
    }

    private static CodeTransformerCheckResultDTO result(boolean success) {
        CodeTransformerCheckResultDTO result = new CodeTransformerCheckResultDTO();
        result.setIsSuccess(success);
        result.setMessage(success ? "Test successful" : "1 of 3 results do not match the goal");
        return result;
    }

    private static CodeTransformerDTO code(FunctionExecutionMode mode) {
        CodeTransformerDTO code = new CodeTransformerDTO();
        code.setId(3L);
        code.setName(mode == FunctionExecutionMode.CELL ? "Upper case" : "Join names");
        code.setMode(mode);
        code.setStatus(CodeTransformerStatus.READY);
        code.setImageName("fl-net/code/transformer-3:abc");
        code.setReturnKeys(List.of());
        return code;
    }
}
