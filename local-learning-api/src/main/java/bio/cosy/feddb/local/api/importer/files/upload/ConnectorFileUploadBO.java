package bio.cosy.feddb.local.api.importer.files.upload;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.importer.ImportPhaseTimer;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadRequestDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesAO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesMapper;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysis;
import bio.cosy.feddb.local.api.importer.files.read.FileAnalysisBO;
import bio.cosy.feddb.local.api.importer.files.read.TableReadSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ConnectorFileUploadBO
        extends BaseFileBO<ConnectorFilesDTO, ConnectorFilesEntity, ConnectorFilesAO, ConnectorFilesMapper> {

    @Inject
    FileAnalysisBO fileAnalysisBO;

    @Inject
    CohortAO cohortAO;

    public List<ConnectorFilesDetailDTO> uploadFileForCohort(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            String keycloakId,
            String importId
    ) {
        requireCohort(cohortId);

        List<ConnectorFilesDetailDTO> results = new ArrayList<>(request.getFiles().size());
        for (FileUpload file : request.getFiles()) {
            results.add(detailOf(uploadFile(
                    cohortId, file, request.getSettings(), null, keycloakId, importId)));
        }
        return results;
    }

    /**
     * Stores and profiles one upload. Reuploads use the same path, with the previous file's parsing
     * settings as a fallback when the request does not repeat them.
     */
    protected ConnectorFilesEntity uploadFile(
            Long cohortId,
            FileUpload file,
            ConnectorFileUploadSettingsDTO requested,
            FileParsingSettingsDTO fallbackParsingSettings,
            String keycloakId,
            String importId
    ) {
        FileParsingSettingsDTO parserSettings = parsingSettings(requested, fallbackParsingSettings, file);
        int previewRows = requested == null
                ? ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS
                : requested.previewRowsOrDefault();

        ImportPhaseTimer uploadTimer = ImportPhaseTimer.started("Upload", "%s", file.fileName());
        ConnectorFilesDTO newDto = new ConnectorFilesDTO();
        newDto.setIsSupportFile(requested != null && Boolean.TRUE.equals(requested.getSupportFile()));
        newDto.setCohortId(cohortId);

        ConnectorFilesEntity entity = createEntity(newDto, keycloakId, file);
        entity.setUploadSettings(parserSettings);

        FileAnalysis analysis = fileAnalysisBO.analyze(
                file.uploadedFile().toFile(),
                TableReadSpec.forAnalysis(parserSettings, previewRows, true, true),
                null,
                previewRows,
                importId);
        List<ConnectorFileUploadInfoDTO> uploadInfo = analysis.uploadInfo();
        if (uploadInfo.isEmpty()) {
            throw new BadRequestException("The uploaded file contains no readable tabular data");
        }
        entity.setUploadInfo(uploadInfo);
        entity.setPreviewData(analysis.previewData());
        uploadTimer.done("file %s profiled into %d logical table(s)", entity.getId(), uploadInfo.size());
        return entity;
    }

    protected ConnectorFilesDetailDTO detailOf(ConnectorFilesEntity entity) {
        ConnectorFilesDetailDTO detail = mapper.entityToDetailDto(entity);
        detail.setFileExists(true);
        detail.setRuns(List.of());
        return detail;
    }

    /** Removes both the database row and its PostgreSQL large object. */
    protected void deleteFile(ConnectorFilesEntity entity) {
        if (entity.getLargeObjectId() != null) {
            deleteStoredFile(entity.getLargeObjectId());
        }
        ao.delete(entity);
    }

    private static FileParsingSettingsDTO parsingSettings(
            ConnectorFileUploadSettingsDTO requested,
            FileParsingSettingsDTO fallback,
            FileUpload file
    ) {
        if (requested != null && requested.getFileType() != null) {
            return requested.toFileParsingSettings();
        }
        if (fallback != null && fallback.getFileType() != null) {
            return fallback;
        }
        return FileParsingSettingsDTO.forFileName(file.fileName());
    }

    public ConnectorFilesDTO uploadFileForCohort(
            Long cohortId,
            File file,
            String keycloakId,
            FileParsingSettingsDTO parsingSettings
    ) {
        return uploadFileForCohort(cohortId, file, file.getName(), keycloakId, parsingSettings);
    }


    public ConnectorFilesDTO uploadFileForCohort(
            Long cohortId,
            File file,
            String fileName,
            String keycloakId,
            FileParsingSettingsDTO parsingSettings
    ) {
        requireCohort(cohortId);
        FileParsingSettingsDTO effectiveParsingSettings = parsingSettings != null
                && parsingSettings.getFileType() != null
                ? parsingSettings
                : FileParsingSettingsDTO.forFileName(fileName);
        ConnectorFilesDTO newDto = new ConnectorFilesDTO();
        newDto.setIsSupportFile(false);
        newDto.setCohortId(cohortId);
        ImportPhaseTimer uploadTimer = ImportPhaseTimer.started("Upload", "%s", fileName);
        ConnectorFilesEntity entity = createEntity(newDto, keycloakId, file, fileName);
        entity.setUploadSettings(effectiveParsingSettings);
        FileAnalysis analysis = fileAnalysisBO.analyze(
                file,
                TableReadSpec.forAnalysis(effectiveParsingSettings,
                        ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS, true, true),
                null,
                ConnectorFileUploadSettingsDTO.DEFAULT_PREVIEW_ROWS);
        entity.setUploadInfo(analysis.uploadInfo());
        entity.setPreviewData(analysis.previewData());
        uploadTimer.done("file %s profiled into %d logical table(s)",
                entity.getId(), analysis.uploadInfo().size());
        return mapper.entityToDto(entity);
    }

    private void requireCohort(Long cohortId) {
        cohortAO.findByIdOptional(cohortId)
                .orElseThrow(() -> new NotFoundException("Cohort not found with id: " + cohortId));
    }
}
