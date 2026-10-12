package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.local.api.importer.files.*;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysis;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysisBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.io.File;
import java.util.List;


@ApplicationScoped
public class HarmonizationFileBO
        extends BaseFileBO<ConnectorFilesDTO, ConnectorFilesEntity, ConnectorFilesAO, ConnectorFilesMapper> {

    static final int PREVIEW_ROWS = ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS;

    @Inject
    FileAnalysisBO fileAnalysisBO;

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
}
