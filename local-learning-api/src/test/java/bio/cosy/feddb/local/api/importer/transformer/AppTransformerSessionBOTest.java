package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.AppTransformerSessionFailure;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.local.execution.run.WorkflowExecutionBO;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppTransformerSessionBOTest {

    @Test
    void sendsEachBatchToTheSameConnectedAppAndStopsItOnClose() {
        AppTransformerSessionBO session = session();
        ConnectorTransformerDTO transformer = appTransformer();
        open(session, transformer);
        WebSocketConnection socket = mock(WebSocketConnection.class);
        when(session.runBO.connectionOf(11L)).thenReturn(Optional.of(socket));
        List<Map<String, Object>> rows = List.of(row("patient_nbr", "8222157", "diag_1", "250.83"));
        AtomicReference<Long> batchId = new AtomicReference<>();
        when(session.appRunBO.expectBatch(eq(11L), anyLong())).thenAnswer(call -> {
            batchId.set(call.getArgument(1));
            return CompletableFuture.completedFuture(new TableData(List.of("diag_combined"),
                    List.of(Map.of("diag_combined", "250.83|?")), null));
        });
        when(socket.sendText(any(Object.class))).thenAnswer(call -> {
            AppMessageWrapperDTO<?> message = call.getArgument(0);
            assertEquals(AppMessageTypeEnum.START_PREDICTION, message.getType());
            StartRunDTO start = (StartRunDTO) message.getMessage();
            assertEquals(batchId.get(), start.getId());
            assertTrue(start.getId() < 0);
            assertEquals(rows, start.getInputData().get("input"));
            assertEquals(transformer.getHyperparams(), start.getHyperParams());
            return Uni.createFrom().voidItem();
        });

        for (int index = 0; index < 2; index++) {
            List<Map<String, Object>> output = session.transform(1L, transformer, rows);
            assertEquals("8222157", output.getFirst().get("patient_nbr"));
            assertEquals("250.83|?", output.getFirst().get("diag_combined"));
        }
        verify(session.appRunBO, never()).endSession(anyLong());
        session.close(1L);
        session.close(1L);

        verify(session.appRunBO, times(1)).startTransactional(any(), any(), any(), any(), any(), eq(true));
        verify(socket, times(2)).sendText(any(Object.class));
        verify(session.appRunBO, times(1)).endSession(11L);
        verify(session.appRunBO, times(2)).forgetBatch(anyLong());
    }

    @Test
    void failedBatchEndsTheAttemptAndClearsItsPendingResult() {
        AppTransformerSessionBO session = session();
        ConnectorTransformerDTO transformer = appTransformer();
        open(session, transformer);
        WebSocketConnection socket = mock(WebSocketConnection.class);
        when(session.runBO.connectionOf(11L)).thenReturn(Optional.of(socket));
        when(session.appRunBO.expectBatch(eq(11L), anyLong())).thenReturn(new CompletableFuture<>());
        when(socket.sendText(any(Object.class))).thenReturn(Uni.createFrom().failure(new IllegalStateException("Socket closed")));

        assertThrows(AppTransformerSessionFailure.class,
                () -> session.transform(1L, transformer, List.of(Map.of("patient_nbr", "8222157"))));

        verify(session.appRunBO).failAttempt(eq(11L), contains("Socket closed"));
        verify(session.appRunBO).forgetBatch(anyLong());
        session.close(1L);
    }

    @Test
    void startupFailureStopsAlreadyConnectedAppsAndTheUnconnectedAttempt() {
        AppTransformerSessionBO session = session();
        ConnectorTransformerDTO first = appTransformer();
        first.setId(4L);
        ConnectorTransformerDTO second = appTransformer();
        second.setId(5L);
        ExecutionRunDTO secondRun = new ExecutionRunDTO();
        secondRun.setId(22L);
        when(session.appRunBO.startTransactional(eq(second), any(), any(), any(), any(), eq(true))).thenReturn(secondRun);
        when(session.appRunBO.connectedAttemptId(22L)).thenReturn(Uni.createFrom().failure(
                new AppTransformerSessionFailure("Container could not be started")));
        ConnectorDTO connector = new ConnectorDTO();
        connector.setTransformer(List.of(first, second));
        ConnectorRunDTO run = new ConnectorRunDTO();
        run.setId(1L);

        assertThrows(AppTransformerSessionFailure.class, () -> session.open(connector, run));

        verify(session.appRunBO).endSession(11L);
        verify(session.workflowExecutionBO).stopTransactional(22L);
        verify(session.appRunBO).forgetSession(22L);
        session.close(1L);
        verify(session.appRunBO, times(1)).endSession(11L);
    }

    @Test
    void previewSubscriberFailureStopsRunEvenBeforeAnAttemptExists() {
        AppTransformerSessionBO session = session();

        assertThrows(IllegalStateException.class, () -> session.openPreview(appTransformer(), "user", run -> {
            throw new IllegalStateException("Preview disconnected");
        }));

        verify(session.workflowExecutionBO).stopTransactional(21L);
        verify(session.appRunBO).forgetSession(21L);
    }

    private static AppTransformerSessionBO session() {
        AppTransformerSessionBO session = new AppTransformerSessionBO();
        session.appRunBO = mock(ConnectorAppRunBO.class);
        session.runBO = mock(ExecutionRunBO.class);
        session.workflowExecutionBO = mock(WorkflowExecutionBO.class);
        ExecutionRunDTO run = new ExecutionRunDTO();
        run.setId(21L);
        when(session.appRunBO.startTransactional(any(), any(), any(), any(), any(), eq(true))).thenReturn(run);
        when(session.appRunBO.connectedAttemptId(21L)).thenReturn(Uni.createFrom().item(11L));
        return session;
    }

    private static void open(AppTransformerSessionBO session, ConnectorTransformerDTO transformer) {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setTransformer(List.of(transformer));
        ConnectorRunDTO run = new ConnectorRunDTO();
        run.setId(1L);
        session.open(connector, run);
    }

    @Test
    void appOutputKeepsTheColumnsTheAppDidNotReturn() {
        ConnectorTransformerDTO transformer = appTransformer();
        List<Map<String, Object>> sent = List.of(
                row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"),
                row("patient_nbr", "55629189", "diag_1", "599", "diag_2", "276"));
        // What a combine app hands back: its own dataframe, which is not the row it was sent.
        List<Map<String, Object>> returned = List.of(
                Map.of("diag_combined", "250.83|?"),
                Map.of("diag_combined", "599|276"));

        List<Map<String, Object>> merged = ConnectorHelper.mergeRows(sent, returned, transformer);

        // Without this the patient id was gone after the first app step and every patient failed to
        // load with "Every mapped row must have an external patient id".
        assertEquals("8222157", merged.get(0).get("patient_nbr"));
        assertEquals("250.83", merged.get(0).get("diag_1"));
        assertEquals("250.83|?", merged.get(0).get("diag_combined"));
        assertEquals("599|276", merged.get(1).get("diag_combined"));
    }

    @Test
    void appOutputWinsOverTheColumnItRewrote() {
        List<Map<String, Object>> sent = List.of(row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"));
        List<Map<String, Object>> returned = List.of(Map.of("diag_1", "250", "diag_2", "83"));

        List<Map<String, Object>> merged = ConnectorHelper.mergeRows(sent, returned, appTransformer());

        assertEquals("250", merged.get(0).get("diag_1"));
        assertEquals("83", merged.get(0).get("diag_2"));
        assertEquals("8222157", merged.get(0).get("patient_nbr"));
    }

    @Test
    void aDifferentNumberOfRowsIsTakenAsItIs() {
        List<Map<String, Object>> sent = List.of(
                row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"),
                row("patient_nbr", "8222157", "diag_1", "599", "diag_2", "276"));
        // An app that filters or splits has changed the row set, so there is nothing to line up.
        List<Map<String, Object>> returned = List.of(Map.of("diag_combined", "250.83|?"));

        List<Map<String, Object>> merged = ConnectorHelper.mergeRows(sent, returned, appTransformer());

        assertEquals(1, merged.size());
        assertEquals(Map.of("diag_combined", "250.83|?"), merged.get(0));
    }

    private static ConnectorTransformerDTO appTransformer() {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setAppImage("transformer-combine-rows:qfyjTIKg");
        return transformer;
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            row.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return row;
    }
}
