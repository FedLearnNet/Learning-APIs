package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.api.model.sub.result.ModelFederatedExperimentResultBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Resolves the {@code appId}/{@code appVersionId} the platform aggregator's own task body never
 * carries (see {@link ProjectFederatedExperimentAggregatorBO#getStartup}) from the step's workflow
 * node, then stores the uploaded model exactly as a clinic-relayed federated model would be stored
 * (see {@link ModelFederatedExperimentResultBO#createModel}) - the platform aggregator just reaches
 * that same storage directly instead of through local-learning-api's relay.
 */
@ApplicationScoped
public class ProjectFederatedExperimentAggregatorModelBO {

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

    @Inject
    ProjectFederatedExperimentStepBO stepBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Inject
    ModelFederatedExperimentResultBO modelResultBO;

    @Inject
    ModelSubBO modelSubBO;

    /**
     * Stores the model file the platform aggregator uploaded for the given step, resolving the
     * app/app-version to store it under from the step's own workflow node. Private models are stored
     * too, like a clinic's model; every new model version starts unpublished.
     *
     * @param stepId    the platform aggregator's step id (the {@code StartRunDTO.id} it was started with)
     * @param file      the uploaded model file and its metadata
     * @param keycloakId the identity to attribute the stored file to
     */
    public void uploadModel(final Long stepId, final ModelSubDataDTO file, final String keycloakId) {
        ProjectFederatedExperimentStepEntity step = stepBO.findEntityByIdOptional(stepId)
                .orElseThrow(() -> new NotFoundException("Step with id " + stepId + " not found"));
        ProjectFederatedExperimentEntity experiment = step.getExperiment();

        WorkflowDTO workflow = workflowBO.entityToDto(experiment.getWorkflow());
        WorkflowNodeDetailDTO node = baseWorkflowEngine.findByNodeId(workflow, step.getWorkflowNode().getNodeId());
        Long appVersionId = node.getFederatedAppVersionId();
        if (appVersionId == null) {
            throw new IllegalStateException("No federated app version configured for step " + stepId);
        }
        FederatedAppVersionDTO appVersion = federatedAppVersionBO.getById(appVersionId);

        ModelSubDTO subDto = modelResultBO.createModel(appVersion.getFederatedAppId(), appVersionId, experiment.getId());
        modelSubBO.addModelSubData(subDto, file, keycloakId);
    }
}
