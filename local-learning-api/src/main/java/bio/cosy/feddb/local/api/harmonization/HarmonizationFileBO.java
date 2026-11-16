package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportHelper;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.*;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysis;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysisBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@ApplicationScoped
public class HarmonizationFileBO
        extends BaseFileBO<ConnectorFilesDTO, ConnectorFilesEntity, ConnectorFilesAO, ConnectorFilesMapper> {

    static final int PREVIEW_ROWS = ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS;

    @Inject
    FileAnalysisBO fileAnalysisBO;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    HarmonizationConnectorMapper connectorMapper;

    public List<ConnectorFilesDetailDTO> list(String keycloakId) {
        return ao.getFilesWithoutCohort(keycloakId).stream()
                .map(mapper::entityToDetailDto)
                .toList();
    }

    @Override
    public ConnectorFilesDetailDTO getById(Long id, String keycloakId) {
        return mapper.entityToDetailDto(getEntityById(id, keycloakId));
    }

    public ConnectorFilesDetailDTO upload(File file, String fileName, String keycloakId) {
        FileParsingSettingsDTO settings = FileParsingSettingsDTO.forFileName(fileName);
        if (settings.getFileType() == FileParsingType.CSV) {
            settings.setDelimiter(delimiter(file));
        }
        ConnectorFilesDTO dto = new ConnectorFilesDTO();
        dto.setIsSupportFile(false);
        ConnectorFilesEntity entity = createEntity(dto, keycloakId, file, fileName);
        entity.setUploadSettings(settings);
        FileAnalysis analysis = fileAnalysisBO.analyze(file,
                TableReadSpec.forAnalysis(settings, PREVIEW_ROWS, true, true), null, PREVIEW_ROWS);
        if (analysis.uploadInfo().isEmpty()) {
            throw new BadRequestException("The file contains no readable tabular data");
        }
        entity.setUploadInfo(analysis.uploadInfo());
        entity.setPreviewData(analysis.previewData());
        return mapper.entityToDetailDto(entity);
    }

    public List<ColumnProfile> profiles(Long id, String keycloakId) {
        List<ConnectorFileUploadInfoDTO> uploadInfo = getEntityById(id, keycloakId).getUploadInfo();
        return uploadInfo == null || uploadInfo.isEmpty() || uploadInfo.getFirst().getColumnProfiles() == null
                ? List.of()
                : uploadInfo.getFirst().getColumnProfiles();
    }

    public Map<String, ColumnProfile> profilesByName(Long id, String keycloakId) {
        return profiles(id, keycloakId).stream()
                .collect(Collectors.toMap(ColumnProfile::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    public List<Map<String, Object>> rows(Long id, SheetMergeResultDTO mergeConfig) {
        try (TableData data = extractBO.loadConnectorData(connectorMapper.toInput(id), null, mergeConfig, null)) {
            return data.getRows();
        }
    }

    public List<Map<String, Object>> previewRows(Long id, SheetMergeResultDTO mergeConfig, int rows, String keycloakId) {
        getEntityById(id, keycloakId);
        try (TableData data = extractBO.loadPreviewData(connectorMapper.toInput(id), null, rows, mergeConfig, null)) {
            return data.getRows();
        }
    }

    public List<Map<String, Object>> sample(Long id, int rows, String keycloakId) {
        Random random = new Random(id);
        List<ColumnProfile> profiles = profiles(id, keycloakId);
        return IntStream.range(0, rows).mapToObj(row -> {
            Map<String, Object> sample = new LinkedHashMap<>();
            profiles.forEach(profile -> sample.put(profile.name(), HarmonizationProfileHelper.sampleValue(profile, random)));
            return sample;
        }).toList();
    }

    public ConnectorFilesDetailDTO save(List<Map<String, Object>> rows, String fileName, String keycloakId) {
        Path csv = PatientDataExportHelper.writeExportedRowsToTempFile(rows);
        try {
            return upload(csv.toFile(), fileName, keycloakId);
        } finally {
            FileUtils.deleteQuietly(csv.toFile());
        }
    }
}
