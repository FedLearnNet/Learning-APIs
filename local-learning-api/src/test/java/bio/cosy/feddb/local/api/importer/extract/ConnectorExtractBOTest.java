package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorExtractBOTest {

    @Test
    void previewUsesPersistedTableDataWithoutLoadingTheLargeObject() {
        ConnectorFilesBO filesBO = mock(ConnectorFilesBO.class);
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        @SuppressWarnings("unchecked")
        Instance<ConnectorExtractBO> self = mock(Instance.class);
        ConnectorExtractBO extractBO = new ConnectorExtractBO();
        extractBO.filesBO = filesBO;
        extractBO.fileHandlerBO = fileHandlerBO;
        extractBO.self = self;
        when(self.get()).thenReturn(extractBO);

        FileUploadSettingsDTO input = new FileUploadSettingsDTO();
        input.setFileId(42L);
        ConnectorFilesDTO file = new ConnectorFilesDTO();
        file.setId(42L);
        FileParsingSettingsDTO parsingSettings = new FileParsingSettingsDTO();
        parsingSettings.setFileType(FileParsingType.CSV);
        file.setUploadSettings(parsingSettings);
        TableSample stored = new TableSample(
                List.of("patient_id"),
                List.of(Map.of("patient_id", "p1")),
                List.of()
        );
        TableData expected = new TableData(
                stored.columns(), stored.rows(), stored.columnProfiles());

        when(filesBO.getById(42L)).thenReturn(file);
        when(filesBO.getPreviewData(42L)).thenReturn(Map.of("0", stored));
        when(fileHandlerBO.getFirstPreviewTableData(
                org.mockito.ArgumentMatchers.<Map<String, TableSample>>any(),
                any(),
                eq(10),
                eq(null),
                eq(null)
        ))
                .thenReturn(expected);

        TableData result = extractBO.loadPreviewData(input, 7L, 10, null, null);

        assertSame(expected, result);
        verify(filesBO, never()).loadFile(any(Long.class));
        verify(fileHandlerBO).getFirstPreviewTableData(any(), any(), eq(10), eq(null), eq(null));
    }

    @Test
    void previewDoesNotFallBackToTheOriginalFileWhenStoredDataIsMissing() {
        ConnectorFilesBO filesBO = mock(ConnectorFilesBO.class);
        TabularFileReaderBO fileHandlerBO = mock(TabularFileReaderBO.class);
        @SuppressWarnings("unchecked")
        Instance<ConnectorExtractBO> self = mock(Instance.class);
        ConnectorExtractBO extractBO = new ConnectorExtractBO();
        extractBO.filesBO = filesBO;
        extractBO.fileHandlerBO = fileHandlerBO;
        extractBO.self = self;
        when(self.get()).thenReturn(extractBO);

        FileUploadSettingsDTO input = new FileUploadSettingsDTO();
        input.setFileId(42L);
        ConnectorFilesDTO file = new ConnectorFilesDTO();
        file.setId(42L);
        when(filesBO.getById(42L)).thenReturn(file);
        when(filesBO.getPreviewData(42L)).thenReturn(Map.of());

        assertThrows(
                jakarta.ws.rs.NotFoundException.class,
                () -> extractBO.loadPreviewData(input, 7L, 10, null, null)
        );
        verify(filesBO, never()).loadFile(any(Long.class));
    }

}
