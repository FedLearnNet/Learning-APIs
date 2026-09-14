package bio.cosy.feddb.local.services.orch;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowOrchestratorBOTest {
    @Test
    void failedDownloadIsNotConvertedToEmptyResults() {
        var service = new WorkflowOrchestratorBO();
        service.volumeClient = mock(OrchVolumeServiceClient.class);
        var response = mock(Response.class);
        when(service.volumeClient.downloadFilesIds(20L, 0L)).thenReturn(response);
        when(response.getStatus()).thenReturn(404);
        when(response.readEntity(String.class)).thenReturn("Output volume does not exist");

        var failure = assertThrows(IllegalStateException.class, () -> service.getFiles(20L, 0L));

        assertTrue(failure.getMessage().contains("404"));
        assertTrue(failure.getMessage().contains("Output volume does not exist"));
        verify(response).close();
    }

    @Test
    void clientConnectionFailurePropagates() {
        var service = new WorkflowOrchestratorBO();
        service.volumeClient = mock(OrchVolumeServiceClient.class);
        var failure = new IllegalStateException("Connection was closed");
        when(service.volumeClient.downloadFilesIds(20L, 0L)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.getFiles(20L, 0L)).getCause());
    }

    @Test
    void validEmptyArchiveRemainsAllowed() throws Exception {
        var service = new WorkflowOrchestratorBO();
        service.volumeClient = mock(OrchVolumeServiceClient.class);
        var response = mock(Response.class);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.finish();
        }
        when(service.volumeClient.downloadFilesIds(20L, 0L)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(InputStream.class)).thenReturn(new ByteArrayInputStream(bytes.toByteArray()));

        assertTrue(service.getFiles(20L, 0L).isEmpty());
        verify(response).close();
    }
}
