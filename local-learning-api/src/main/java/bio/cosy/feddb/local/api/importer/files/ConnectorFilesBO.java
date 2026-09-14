package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunAO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunMapper;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessageLevels;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ConnectorFilesBO extends BaseFileBO<ConnectorFilesDTO, ConnectorFilesEntity, ConnectorFilesAO, ConnectorFilesMapper> {

    @Inject
    ConnectorAO connectorAO;

    @Inject
    ConnectorRunAO runAO;

    @Inject
    ConnectorRunMapper runMapper;

    @Inject
    ConnectorRunRunMessagesBO runMessagesBO;

    public List<ConnectorFilesDTO> getFiles(Long cohortId) {
        return mapper.entitiesToDtos(ao.getFilesByCohort(cohortId));
    }

    public Map<String, TableSample> getPreviewData(Long fileId) {
        if (fileId == null) {
            return Map.of();
        }
        return ao.findByIdOptional(fileId)
                .map(entity -> immutablePreviewData(entity.getPreviewData()))
                .orElseGet(Map::of);
    }

    private static Map<String, TableSample> immutablePreviewData(Map<String, TableSample> previewData) {
        return previewData == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(previewData));
    }

    public boolean deleteFile(Long cohortId, Long fileId) {
        return ao.deleteFileByCohortId(cohortId, fileId) > 0;
    }

    public ConnectorFilesDetailDTO getById(Long cohortId, Long fileId) {
        return ao.getFileByCohortId(cohortId, fileId)
                .map(this::detailOf)
                .orElseGet(() -> missingFileDetail(cohortId, fileId));
    }


    private ConnectorFilesDetailDTO detailOf(ConnectorFilesEntity entity) {
        ConnectorFilesDetailDTO dto = mapper.entityToDetailDto(entity);
        dto.setFileExists(true);
        dto.setRuns(getRunsForFile(dto.getId()));
        return dto;
    }

    public ConnectorFilesDetailDTO getFileInfo(Long cohortId) {
        return ao.getFirstFileByCohortId(cohortId)
                .map(this::detailOf)
                .orElseGet(() -> missingFileDetail(cohortId, null));
    }

    private ConnectorFilesDetailDTO missingFileDetail(Long cohortId, Long fileId) {
        ConnectorFilesDetailDTO dto = new ConnectorFilesDetailDTO();
        dto.setCohortId(cohortId);
        if (fileId != null) {
            dto.setId(fileId);
        }
        dto.setFileExists(false);
        dto.setUploadInfo(List.of());
        dto.setRuns(List.of());
        return dto;
    }

    private List<ConnectorRunDTO> getRunsForFile(Long fileId) {
        return connectorAO.findByInputFileId(fileId).stream()
                .flatMap(connector -> runAO.getAllForConnector(connector.getId()).stream())
                .map(runMapper::entityToDto)
                .sorted(Comparator.comparing(ConnectorRunDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public FileResult downloadConnectorFile(Long cohortId, Long fileId) {
        Optional<ConnectorFilesEntity> entityOpt = ao.getFileByCohortId(cohortId, fileId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("File not found");
        }
        return loadFileResult(entityOpt.get());
    }

    @Transactional
    public void cleanupFileIfNotNeededTransactional(ConnectorDTO connectorDTO, FileUploadSettingsDTO fileSettings, Long runId) {
        if (connectorDTO == null || connectorDTO.getId() == null || fileSettings == null || fileSettings.getFileId() == null) {
            Log.debug("Skipping connector file cleanup because connector or file settings are incomplete");
            return;
        }

        Long fileId = fileSettings.getFileId();
        List<Long> otherConnectorIds = connectorAO.findByInputFileId(fileId).stream()
                .map(ConnectorEntity::getId)
                .filter(connectorId -> !connectorDTO.getId().equals(connectorId))
                .toList();

        if (!otherConnectorIds.isEmpty()) {
            Log.infof(
                    "Skipping cleanup for connector file %d because it is still used by connector(s): %s",
                    fileId,
                    otherConnectorIds
            );
            runMessagesBO.createTransactional("Input file was not deleted because it is still used by other connectors", ConnectorRunMessageLevels.INFO, runId);
            return;
        }

        ao.findByIdOptional(fileId).ifPresentOrElse(file -> {
            deleteStoredFile(file.getLargeObjectId());
            ao.delete(file);
            Log.infof("Deleted connector file %d after successful import by connector %d", fileId, connectorDTO.getId());
            runMessagesBO.createTransactional("Input file was deleted", ConnectorRunMessageLevels.INFO, runId);
        }, () -> Log.infof("Skipping cleanup for connector file %d because it no longer exists", fileId));
    }

}
