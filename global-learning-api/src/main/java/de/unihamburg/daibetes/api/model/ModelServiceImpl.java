package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.api.model.version.ModelVersionBO;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class ModelServiceImpl implements ModelService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ModelBO modelBO;

    @Inject
    ModelVersionBO modelVersionBO;

    @Inject
    ModelSubBO modelSubBO;

    @Inject
    DataAnalysisPredictionBO modelPredictionBO;


    @Override
    public List<ModelDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.getAll(keycloakId);
    }

    @Override
    public List<ModelDTO> listMyModels(Long appId, Long experimentId, Long federatedExperimentId) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.getMyModels(keycloakId, appId, experimentId, federatedExperimentId);
    }

    @Override
    public ModelVersionDTO createForImage(ModelCreateForImageDTO request) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.createForImage(request, keycloakId);
    }


    @Override
    public ModelDetailDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.getById(id, keycloakId);
    }

    @Override
    @Transactional
    public ModelDTO update(Long id, ModelDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.update(id, dto, keycloakId);
    }

    @Override
    @Transactional
    public ModelVersionDTO update(Long id, Long modelVersionId, ModelVersionDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelVersionBO.update(id, modelVersionId, dto, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        modelBO.deleteById(id, keycloakId);
        return Response.ok().build();
    }

    @Override
    @Transactional
    public Response selectSubModel(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        ModelSubDTO selected = modelSubBO.selectModel(id, keycloakId);
        return Response.ok(selected).build();
    }

    @Override
    public List<DataAnalysisPredictionDTO> listAllExperiment() {
        String keycloakId = userIdentity.getKeycloakId();

        return modelPredictionBO.getAll(keycloakId);
    }

    @Override
    public List<DataAnalysisPredictionDTO> listPredictions(Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return modelPredictionBO.getAllForModel(id, keycloakId);
    }

    @Override
    @Transactional
    public Response createPredictions(Long modelId, Long subId, DataAnalysisCreatePredictionDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        createDTO.setModelSubId(subId);
        if (createDTO.getModelSubId() == null &&
                createDTO.getModelVersionId() == null &&
                createDTO.getAppVersionId() == null) {
            throw new BadRequestException("Execution id has to be set (Model or App id)");
        }

        DataAnalysisPredictionDTO modelPredictionDTO = modelPredictionBO.create(modelId, createDTO, keycloakId);
        return Response.status(Response.Status.CREATED).entity(modelPredictionDTO).build();
    }

    @Override
    public ModelSubDTO retrieveForExperimentRun(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelSubBO.getByRunId(id, keycloakId);
    }

    @Override
    public ModelSubDTO retrieveForFederatedExperimentRun(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelSubBO.getByFederatedRunId(id, keycloakId);
    }

    @Override
    public ModelVersionDTO retrieveForExperiment(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelVersionBO.getByExperiment(id, keycloakId);
    }

    @Override
    public ModelVersionDTO retrieveForFederatedExperiment(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelVersionBO.getByFederatedExperiment(id, keycloakId);
    }
}
