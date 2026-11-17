package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                stored.getColumns(), stored.getRows(), stored.getColumnProfiles());

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

    @Test
    void aTenRowSampleFromAnOlderUploadReadsMoreRowsFromTheFile() {
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
        TableSample stored = new TableSample(List.of("patient_id"), tenRows(), List.of());
        File original = new File("clinic_001.csv");
        TableData expected = new TableData(List.of("patient_id"), tenRows(), List.of());

        when(filesBO.getById(42L)).thenReturn(file);
        when(filesBO.getPreviewData(42L)).thenReturn(Map.of("0", stored));
        when(filesBO.loadFile(42L)).thenReturn(original);
        when(fileHandlerBO.getFirstTableData(eq(original), any(TableReadSpec.class), eq(null), eq(null), eq(null)))
                .thenReturn(expected);

        TableData result = extractBO.loadPreviewData(input, 7L, 50, null, null);

        assertSame(expected, result);
        verify(fileHandlerBO, never()).getFirstPreviewTableData(any(), any(), any(), any(), any());
    }

    @Test
    void storedSamplesAreOnlyTooShortForMoreThanTenRows() {
        Map<String, TableSample> ten = Map.of("0", new TableSample(List.of("patient_id"), tenRows(), List.of()));
        Map<String, TableSample> short3 = Map.of("0", new TableSample(
                List.of("patient_id"), tenRows().subList(0, 3), List.of()));

        assertTrue(ConnectorExtractBO.storedPreviewTooShort(ten, 50));
        assertFalse(ConnectorExtractBO.storedPreviewTooShort(ten, 10));
        assertFalse(ConnectorExtractBO.storedPreviewTooShort(ten, null));
        assertFalse(ConnectorExtractBO.storedPreviewTooShort(short3, 50));
    }

    private static List<Map<String, Object>> tenRows() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(Map.of("patient_id", "p" + i));
        }
        return rows;
    }
}
