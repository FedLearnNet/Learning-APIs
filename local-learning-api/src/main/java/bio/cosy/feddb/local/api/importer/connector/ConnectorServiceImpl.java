package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryRollbackBO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.run.*;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewBO;
import bio.cosy.feddb.local.api.importer.run.preview.cache.ConnectorPreviewTransformationCacheBO;
import bio.cosy.feddb.local.api.importer.run.preview.PreviewResponseDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBulkRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationResponseDTO;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class ConnectorServiceImpl implements ConnectorService {

    public static final String CONNECTOR_RUN_CHANNEL = "connector-run-info";

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorValidationBO validationBO;

    @Inject
    PreviewValidationBO previewValidationBO;

    @Inject
    ConnectorRunBO runBO;

    @Inject
    ConnectorPreviewBO previewBO;

    @Inject
    ConnectorPreviewTransformationCacheBO previewCacheBO;

    @Inject
    PatientDataEntryRollbackBO rollbackBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    CohortBO cohortBO;

    @Inject
    ConnectorFilesBO connectorFilesBO;

    @Inject
    @Channel(CONNECTOR_RUN_CHANNEL)
    Multi<ConnectorRunDTO> runProcessInfo;

    @Override
    @Blocking
    public List<ConnectorDTO> listConnectors(Long cohortId) {
        String keycloakId = userIdentity.getKeycloakId();
        return connectorBO.getAllByCohortId(cohortId, keycloakId);
    }

    @Override
    @Transactional
    public ConnectorDTO createConnector(boolean raw, ConnectorDTO connectorDTO) {
        cohortMemberAuthBO.checkForEdit(connectorDTO.getCohortId(), userIdentity.getKeycloakId());
        return connectorBO.createRawCheck(connectorDTO, raw);
    }

    @Override
    @Transactional
    public ConnectorDTO importFromRemoteUrl(String remoteUrl, Long cohortId) {
        String keycloakId = userIdentity.getKeycloakId();
        cohortMemberAuthBO.checkForEdit(cohortId, keycloakId);
        return connectorBO.importFromRemoteUrl(remoteUrl, cohortId, keycloakId, null);
    }

    @Override
    @Blocking
    public ConnectorDTO getConnector(Long id) {
        return connectorBO.getFileCheckedById(id);
    }

    @Override
    @Transactional
    public ConnectorDTO updateConnector(Long id, ConnectorDTO connectorDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        ConnectorDTO existingConnector = connectorBO.getById(id);
        cohortMemberAuthBO.checkForEdit(existingConnector.getCohortId(), keycloakId);
        if (!Objects.equals(existingConnector.getCohortId(), connectorDTO.getCohortId())) {
            cohortMemberAuthBO.checkForEdit(connectorDTO.getCohortId(), keycloakId);
        }
        return connectorBO.update(id, connectorDTO, keycloakId);
    }

    @Override
    @Transactional
    public ConnectorDTO patchConnector(Long id, ConnectorDTO connectorDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return connectorBO.patch(id, connectorDTO, keycloakId);
    }

    @Override
    @Transactional
    public Response deleteConnector(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        ConnectorDTO connector = connectorBO.getById(id);
        cohortMemberAuthBO.checkForDelete(connector.getCohortId(), keycloakId);
        connectorBO.delete(id);
        return Response.ok().build();
    }

    // Is in theory also blocking but this is dumped into the Uni return block
    @Override
    public Multi<ConnectorRunDTO> runConnector(Long id, boolean deleteExistingPatients, boolean dry) {
        String keycloakId = userIdentity.getKeycloakId();

        //TODO SUPPORT SSE, replace the code and change fe
        /*
        Uni.createFrom().item(() -> runBO.startRun(id, deleteExistingPatients, dry, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(result -> {
                });


        return runProcessInfo.filter(p -> p.getConnectorId() != null && p.getConnectorId().equals(id));
         */
        return Uni.createFrom().item(() -> {
                ConnectorDTO connector = connectorBO.getById(id);
                cohortMemberAuthBO.checkForEditPatients(connector.getCohortId(), keycloakId);
                if (cohortBO.isDeletionInProgress(connector.getCohortId())) {
                    throw new WebApplicationException(
                            "Cohort deletion is in progress.",
                            Response.Status.CONFLICT);
                }
                return runBO.startRun(id, deleteExistingPatients, dry, keycloakId);
            })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .toMulti();
    }

    @Override
    @Transactional
    public PreviewResponseDTO previewConnector(ConnectorConfigDTO connectorConfigDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        cohortMemberAuthBO.checkForEdit(connectorConfigDTO.getCohortId(), keycloakId);
        return previewBO.preview(connectorConfigDTO);
    }

    @Override
    public void invalidatePreviewCache(Long connectorId, Integer fromStep) {
        String keycloakId = userIdentity.getKeycloakId();
        ConnectorDTO connector = connectorBO.getById(connectorId);
        cohortMemberAuthBO.checkForEdit(connector.getCohortId(), keycloakId);
        if (fromStep == null) {
            previewCacheBO.invalidateAll(connectorId);
            return;
        }
        previewCacheBO.invalidateFrom(connectorId, fromStep);
    }

    @Override
    @Transactional
    public PreviewResponseDTO previewPivotTable(ConnectorConfigDTO connectorConfigDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        Long cohortId = resolvePivotPreviewCohortId(connectorConfigDTO);
        cohortMemberAuthBO.checkForEdit(cohortId, keycloakId);
        connectorConfigDTO.setCohortId(cohortId);
        return previewBO.previewPivot(connectorConfigDTO);
    }

    private Long resolvePivotPreviewCohortId(ConnectorConfigDTO connectorConfigDTO) {
        if (connectorConfigDTO == null) {
            throw new BadRequestException("Pivot preview configuration is required");
        }
        Long requestedCohortId = connectorConfigDTO.getCohortId();
        if (connectorConfigDTO.getInputConfig() instanceof FileUploadSettingsDTO fileSettings
                && fileSettings.getFileId() != null) {
            ConnectorFilesDTO file = connectorFilesBO.getById(fileSettings.getFileId());
            if (file != null && file.getCohortId() != null) {
                if (requestedCohortId != null && !Objects.equals(requestedCohortId, file.getCohortId())) {
                    throw new BadRequestException("Uploaded file is assigned to a different cohort");
                }
                return file.getCohortId();
            }
        }
        if (requestedCohortId != null) {
            return requestedCohortId;
        }
        throw new BadRequestException("Pivot preview requires a cohort ID or an uploaded file assigned to a cohort");
    }

    @Override
    public Multi<PreviewValidationResponseDTO> validatePreview(PreviewValidationRequestDTO request) {
        String keycloakId = userIdentity.getKeycloakId();
        return Multi.createFrom().deferred(() -> {
                    cohortMemberAuthBO.checkForEdit(request.getCohortId(), keycloakId);
                    return previewValidationBO.validate(request);
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @Override
    @Blocking
    public ConnectorValidationResultDTO checkValidations(Long cohortId, String value, Long schemaId, String mapping) {
        String keycloakId = userIdentity.getKeycloakId();
        cohortMemberAuthBO.checkForEdit(cohortId, keycloakId);
        return validationBO.checkValidations(cohortId, value, schemaId, mapping, keycloakId);
    }

    @Override
    @Blocking
    public List<ConnectorValidationResultDTO> checkValidationsBulk(Long cohortId, List<ConnectorValidationBulkRequestDTO> items) {
        String keycloakId = userIdentity.getKeycloakId();
        cohortMemberAuthBO.checkForEdit(cohortId, keycloakId);
        return validationBO.checkValidationsBulk(cohortId, items, keycloakId);
    }

    @Override
    @Transactional
    public Response rollbackConnectorRun(Long id, Long runId, boolean deleteAudit) {
        rollbackBO.rollbackConnectorRun(runId, deleteAudit);
        return Response.ok().build();
    }
}
