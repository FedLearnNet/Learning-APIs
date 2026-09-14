package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortDetailDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesAO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.notification.WebsocketClientNotificationBO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorBO extends BaseBo<ConnectorDTO, ConnectorEntity, ConnectorAO, ConnectorMapper> {

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    ConnectorRemoteHelper remoteHelper;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ConnectorFilesAO filesAO;

    @Inject
    ConnectorSchemaHelper connectorSchemaHelper;

    @Inject
    CohortBO cohortBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    ConnectorScheduleBO scheduleBO;

    @Inject
    WebsocketClientNotificationBO websocketClientNotificationBO;

    public List<ConnectorDTO> getAllByCohortId(Long cohortId, String keycloakId) {
        cohortMemberAuthBO.checkForMember(cohortId, keycloakId);
        return ao.getAllByCohortId(cohortId)
                .stream()
                .map(mapper::entityToDto)
                .map(this::enhanceDTO)
                .toList();
    }

    public List<SearchResultDTO<ConnectorDTO>> search(String query, String keycloakId) {
        return cohortBO.getAll(keycloakId).stream()
                .flatMap(cohort -> getAllByCohortId(cohort.getId(), keycloakId).stream())
                .map(connector -> {
                    String title = connector.getName();
                    int score = SearchScoreUtil.score(query, title, connector.getDescription(),
                            String.valueOf(connector.getCohortId()));
                    return SearchScoreUtil.toResult(SearchResultType.CONNECTOR, connector, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    public ConnectorDTO create(ConnectorDTO data, boolean checkSchema, boolean acceptNotFoundFile) {
        if (data.getInputConfig() instanceof FileUploadSettingsDTO fileSettings) {
            Long fileId = fileSettings.getFileId();
            if (fileId == null || !filesAO.existById(fileId)) {
                if (!acceptNotFoundFile) {
                    throw new BadRequestException("File with id " + fileId + " does not exist");
                }
            }
        }
        CohortDetailDTO cohort = cohortBO.findById(data.getCohortId());
        if (checkSchema) {
            data = connectorSchemaHelper.validateAndRematchMappingSchemaId(data, cohort);
        }
        ConnectorDTO connector = super.create(data);
        if (connector == null) {
            return null;
        }

        scheduleBO.syncSchedule(connector.getId(), connector.getScheduleSettings());

        if (data.getTransformer() != null && !data.getTransformer().isEmpty()) {
            for (ConnectorTransformerDTO transformer : data.getTransformer()) {
                transformer.setConnectorId(connector.getId());
                ConnectorTransformerDTO created = transformerBO.create(transformer);
                Log.debugf("Created transformer: %d for connector (%d)", created.getId(), created.getConnectorId());
            }
            ConnectorDTO refreshed = reloadConnector(connector.getId());
            if (refreshed != null) {
                return refreshed;
            }
        }

        return connector;
    }

    public ConnectorDTO createRawCheck(ConnectorDTO data, boolean importRaw) {
        return createRawCheck(data, importRaw, null);
    }

    public ConnectorDTO createRawCheck(ConnectorDTO data, boolean importRaw, String name) {
        if (!importRaw) {
            return create(data, false, false);
        }

        setBaseValuesNull(data);
        detachImportedInputFile(data);
        String randomSuffix = HexFormat.of().formatHex(java.security.SecureRandom.getSeed(4));
        if (data.getName() != null) {
            if (StringUtils.isEmpty(name)) {
                data.setName(data.getName() + "_" + randomSuffix);
            } else {
                data.setName(name);
            }
        }

        if (data.getTransformer() != null && !data.getTransformer().isEmpty()) {
            for (ConnectorTransformerDTO transformer : data.getTransformer()) {
                setBaseValuesNull(transformer);
                transformer.setConnectorId(null);
            }
        }

        return create(data, true, true);
    }

    /**
     * Drops the input file reference an imported configuration arrived with.
     *
     * <p>A file id means nothing outside the installation that issued it. Kept as it is, an imported
     * connector points at whatever local file happens to carry that id - a file it was never
     * uploaded for. The connector then looks like it already has its input, and the first file the
     * user does upload is validated against that stranger's columns and refused for not matching a
     * file the user never chose. An imported configuration describes how to process a file; which
     * file that is, is decided here.</p>
     */
    static void detachImportedInputFile(ConnectorDTO data) {
        if (data != null && data.getInputConfig() instanceof FileUploadSettingsDTO fileSettings) {
            fileSettings.setFileId(null);
            fileSettings.setFile(null);
            fileSettings.setFileExists(false);
        }
    }

    public void delete(Long id) {
        if (id == null) {
            return;
        }
        scheduleBO.removeSchedule(id);
        deleteById(id);
    }

    public void deleteAllForCohort(Long cohortId) {
        if (cohortId == null) {
            return;
        }
        List<ConnectorEntity> connectors = ao.getAllByCohortId(cohortId);
        for (ConnectorEntity connector : connectors) {
            scheduleBO.removeSchedule(connector.getId());
            ao.delete(connector);
        }
    }

    public ConnectorDTO patch(Long id, ConnectorDTO data, String keycloakId) {
        Optional<ConnectorEntity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("Connector with id " + id + " not found for patching");
        }
        ConnectorDTO currentConnector = mapper.entityToDto(entity.get());
        data.setVersion(currentConnector.getVersion());
        ConnectorDTO connector = super.update(id, data);
        if (connector == null) {
            throw new NotFoundException("Connector with id " + id + " not found for patching");
        }
        scheduleBO.syncSchedule(connector.getId(), data.getScheduleSettings());
        return connector;
    }

    public ConnectorDTO update(Long id, ConnectorDTO data, String keycloakId) {
        Optional<ConnectorEntity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            setBaseValuesNull(data);
            return create(data, false, false);
        }
        ConnectorDTO currentConnector = mapper.entityToDto(entity.get());
        data.setVersion(currentConnector.getVersion());
        ConnectorDTO connector = super.update(id, data);
        if (connector == null) {
            return null;
        }

        transformerBO.syncTransformers(connector.getId(), data.getTransformer());

        return reloadConnector(connector.getId());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void updateInputConfigTransactional(Long id, ConnectorInputConfigDTO inputConfig) {
        ConnectorEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Connector with id " + id + " not found"));
        entity.setInputConfig(inputConfig);
    }

    public ConnectorDTO importFromRemoteUrl(String remoteUrl, Long cohortId, String keycloakId, String name) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new NotFoundException("Remote URL is empty");
        }

        String raw = remoteHelper.loadRemoteResource(remoteUrl);

        JsonNode root = remoteHelper.parseJsonObject(raw);

        ObjectMapper mapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ConnectorDTO connectorDTO;
        try {
            connectorDTO = mapper.treeToValue(root, ConnectorDTO.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid connector payload: " + e.getMessage(), e);
        }

        if (connectorDTO == null) {
            throw new BadRequestException("Connector payload could not be parsed");
        }

        if (cohortId != null) {
            connectorDTO.setCohortId(cohortId);
        }

        ConnectorDTO importedConnector = createRawCheck(connectorDTO, true, name);
        if (importedConnector != null) {
            websocketClientNotificationBO.notifyConnectorImportCompleted(importedConnector.getCohortId());
        }
        return importedConnector;
    }

    public ConnectorDTO getFileCheckedById(Long id) {
        ConnectorDTO dto = getById(id);
        return enhanceDTO(dto);
    }

    private ConnectorDTO reloadConnector(Long id) {
        ao.flush();
        ao.getEntityManager().clear();
        return getFileCheckedById(id);
    }

    private ConnectorDTO enhanceDTO(ConnectorDTO dto) {
        if (dto.getInputConfig() instanceof FileUploadSettingsDTO fileSettings) {
            Long fileId = fileSettings.getFileId();
            fileSettings.setFileExists(fileId != null && filesAO.existById(fileId));
        }
        return dto;
    }


    public Optional<ConnectorDTO> getAllByCohortIdAndName(Long cohortId, String name) {
        return ao.getAllByCohortId(cohortId, name)
                .map(mapper::entityToDto)
                .map(this::enhanceDTO);
    }

}
