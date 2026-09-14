package bio.cosy.feddb.local.api.importer.statistics;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesAO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformedStatisticsBOTest {

    @Test
    void transformsStoredPreviewWithoutReloadingTheCompleteImport() {
        ConnectorFilesEntity file = new ConnectorFilesEntity();
        file.setLargeObjectId(99L);
        TableData preview = new TableData(
                List.of("age"),
                new ArrayList<>(List.of(new LinkedHashMap<>(java.util.Map.of("age", "42")))),
                new ArrayList<>()
        );
        List<ColumnProfile> profiles = List.of(new ColumnProfile(
                "age", "INTEGER", 1, 0, 1,
                42d, 0d, 42d, 42d, 42d, 42d, 42d,
                List.of(), List.of()
        ));
        StubExtractBO extractBO = new StubExtractBO(preview);

        TransformedStatisticsBO bo = new TransformedStatisticsBO();
        bo.extractBO = extractBO;
        bo.fileHandlerBO = new StubReaderBO(profiles);
        bo.ao = new StubFilesAO(file);
        bo.objectMapper = new ObjectMapper();
        bo.maxRows = 100;
        bo.maxPatients = 10;
        bo.maxDistinctPerColumn = 20;

        FileUploadSettingsDTO input = new FileUploadSettingsDTO();
        input.setFileId(11L);
        List<ColumnProfile> result = bo.getTransformedStatistics(
                7L, input, null, null, null, List.of(), List.of());

        assertEquals(profiles, result);
        assertTrue(extractBO.previewLoaded);
        assertFalse(extractBO.fullImportLoaded);
    }

    private static class StubExtractBO extends ConnectorExtractBO {
        private final TableData preview;
        private boolean previewLoaded;
        private boolean fullImportLoaded;

        private StubExtractBO(TableData preview) {
            this.preview = preview;
        }

        @Override
        public TableData loadPreviewData(
                bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO config,
                Long cohortId,
                int maxRows,
                bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO mergeConfig,
                bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO pivotConfig,
                java.util.Map<String, bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO> uploadInfo
        ) {
            previewLoaded = true;
            return preview;
        }

        @Override
        public TableData loadConnectorData(
                bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO config,
                Long cohortId,
                bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO mergeConfig,
                bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO pivotConfig,
                java.util.Map<String, bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO> uploadInfo
        ) {
            fullImportLoaded = true;
            return null;
        }
    }

    private static class StubReaderBO extends TabularFileReaderBO {
        private final List<ColumnProfile> profiles;

        private StubReaderBO(List<ColumnProfile> profiles) {
            this.profiles = profiles;
        }

        @Override
        public List<ColumnProfile> getColumnProfiles(TableData tableData) {
            return profiles;
        }
    }

    private static class StubFilesAO extends ConnectorFilesAO {
        private final ConnectorFilesEntity file;

        private StubFilesAO(ConnectorFilesEntity file) {
            this.file = file;
        }

        @Override
        public Optional<ConnectorFilesEntity> getFileByCohortId(Long cohortId, Long fileId) {
            return Optional.of(file);
        }
    }
}
