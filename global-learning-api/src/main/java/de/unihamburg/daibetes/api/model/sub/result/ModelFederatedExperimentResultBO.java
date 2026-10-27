package de.unihamburg.daibetes.api.model.sub.result;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import bio.cosy.feddb.core.api.model.result.ModelExperimentResultDTO;
import de.unihamburg.daibetes.api.app.FederatedAppAO;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileBO;
import de.unihamburg.daibetes.api.model.version.ModelVersionBO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.Optional;

@ApplicationScoped
public class ModelFederatedExperimentResultBO {

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ModelBO modelBO;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Inject
    FederatedAppAO federatedAppAO;

    @Inject
    ModelSubBO modelSubBO;

    @Inject
    ModelAccessBO accessBO;

    @Inject
    ModelVersionBO modelVersionBO;

    @Inject
    ModelSubFileBO modelSubFileBO;

    public ModelSubDTO uploadResults(ModelExperimentResultDTO dto, String keycloakId) {
        if (dto.getFiles() == null || dto.getFiles().isEmpty()) {
            throw new BadRequestException("No files uploaded");
        }
        Long appVersionId = dto.getAppVersionId();
        if (appVersionId == null) {
            throw new BadRequestException("App Version ID must be provided");
        }

        ExecutionExperimentEntity experiment = findExperiment(dto.getGlobalFLExperimentUniqueId(), dto.getClinicId());
        if (Boolean.FALSE.equals(experiment.getModelCanBePublic())) {
            Log.warnf("Model of experiment %s cannot be public", experiment.getGlobalUniqueId());
            throw new BadRequestException("Model of experiment " + experiment.getGlobalUniqueId() + " cannot be public");
        }
        FederatedAppVersionDTO appVersion = federatedAppVersionBO.getById(appVersionId);
        Long appId = appVersion.getFederatedAppId();

        ModelSubDTO subVersion = createModel(appId, appVersionId, experiment.getId());

        modelSubFileBO.create(dto, subVersion.getId(), keycloakId);
        return subVersion;
    }

    public ModelSubDTO createModel(Long appId, Long appVersionId, Long experimentId) {
        String name = getName(appId);
        Optional<ModelDTO> oldModelOptional = modelBO.getByAppVersionId(appVersionId);
        ModelDTO newModel;
        if (oldModelOptional.isPresent()) {
            newModel = oldModelOptional.get();
        } else {
            newModel = modelBO.createInitial(appVersionId, name);
            accessBO.create(appId, newModel.getId());
        }
        ModelVersionDTO newVersion = modelVersionBO.createInitialFederated(newModel.getId(), experimentId);
        ModelSubDTO newSubDto = modelSubBO.createFedInitial(newVersion.getId(), experimentId);
        return newSubDto;
    }


    public ExecutionExperimentEntity findExperiment(String globalUniqueExperimentId, String clinicId) {
        if (globalUniqueExperimentId == null || clinicId == null || clinicId.isBlank()) {
            throw new BadRequestException("Experiment ID and Clinic ID must be provided");
        }
        Optional<ExecutionExperimentEntity> entityOptional = experimentAO.find("globalUniqueId", globalUniqueExperimentId).firstResultOptional();
        if (entityOptional.isEmpty()) {
            throw new NotFoundException("No experiment found with ID: " + globalUniqueExperimentId);
        }
        ExecutionExperimentEntity entity = entityOptional.get();
        boolean coordinator = entity.getRuns().stream().flatMap(run -> run.getParticipants().stream())
                .anyMatch(participant -> participant.isCoordinator() && clinicId.equals(participant.getParticipantId()));
        if (!coordinator) {
            throw new BadRequestException("Clinic ID does not match");
        }
        return entity;
    }


    public String getName(Long appId) {
        FederatedAppEntity entity = federatedAppAO.findById(appId);
        if (entity == null) {
            throw new NotFoundException("No app found with ID: " + appId);
        }
        return "Model for App " + entity.getName();
    }
}
