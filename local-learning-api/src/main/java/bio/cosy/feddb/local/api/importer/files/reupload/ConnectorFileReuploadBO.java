package bio.cosy.feddb.local.api.importer.files.reupload;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadRequestDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.files.progress.ImportPhase;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileImportResultDTO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileUploadBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
@Typed(ConnectorFileReuploadBO.class)
public class ConnectorFileReuploadBO extends ConnectorFileUploadBO {

    @Inject
    ConnectorAO connectorAO;

    @Inject
    ImportProgressTracker progress;

    @Inject
    ReuploadValidationBO reuploadValidationBO;


    public ConnectorFileImportResultDTO replaceConnectorInput(
            Long cohortId,
            Long connectorId,
            ConnectorFileUploadRequestDTO request,
            String keycloakId,
            String importId
    ) {
        ConnectorEntity connector = connectorAO.findByIdOptional(connectorId)
                .orElseThrow(() -> new NotFoundException("Connector not found with id: " + connectorId));
        if (connector.getCohort() == null || !cohortId.equals(connector.getCohort().getId())) {
            throw new NotFoundException("Connector not found with id: " + connectorId);
        }

        FileUpload file = request.getFiles().getFirst();
        if (!(connector.getInputConfig() instanceof FileUploadSettingsDTO inputConfig)) {
            throw new BadRequestException("Connector " + connectorId + " does not use a file input");
        }

        Optional<ConnectorFilesEntity> existing = ao.getFileByCohortId(cohortId, inputConfig.getFileId());
        boolean usedByAnotherConnector = existing
                .map(old -> connectorAO.findByInputFileId(old.getId()).stream()
                        .anyMatch(other -> !connectorId.equals(other.getId())))
                .orElse(false);
        ConnectorFilesEntity uploaded = uploadFile(
                cohortId,
                file,
                request.getSettings(),
                previousParsingSettings(existing.orElse(null), inputConfig),
                keycloakId,
                importId);

        if (existing.isPresent()) {
            progress.phase(importId, ImportPhase.COMPARING);
            List<String> storedColumns = ConnectorHelper.filterOurUnsuedColumns(
                    connector, existing.get().getUploadInfo());
            List<String> uploadedColumns = ConnectorHelper.filterOurUnsuedColumns(
                    connector, uploaded.getUploadInfo());
            ConnectorFilesReuploadErrorDTO error = reuploadValidationBO.validateUsedColumns(
                    storedColumns, uploadedColumns);
            if (error != null) {
                deleteFile(uploaded);
                return ConnectorFileImportResultDTO.refused(error);
            }
        }

        inputConfig.setFileId(uploaded.getId());
        connector.setInputConfig(inputConfig);
        connectorAO.persist(connector);
        existing.filter(old -> !usedByAnotherConnector)
                .ifPresent(this::deleteFile);
        return ConnectorFileImportResultDTO.accepted(detailOf(uploaded));
    }

    private FileParsingSettingsDTO previousParsingSettings(
            ConnectorFilesEntity existing,
            FileUploadSettingsDTO inputConfig
    ) {
        if (existing != null
                && existing.getUploadSettings() != null
                && existing.getUploadSettings().getFileType() != null) {
            return existing.getUploadSettings();
        }
        return inputConfig.toFileParsingSettings();
    }
}
