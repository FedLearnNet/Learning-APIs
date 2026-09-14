package de.unihamburg.daibetes.api.project.experiment;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentDetailDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepDTO;
import de.unihamburg.daibetes.api.project.experiment.local.CreateProjectLocalExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDetailDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ProjectExperimentServiceImpl implements ProjectExperimentService {

    public static final String EXPERIMENT_LOCAL_CHANNEL = "experiment-local-info";
    public static final String EXPERIMENT_FED_CHANNEL = "experiment-fed-info";
    public static final String EXPERIMENT_LOCAL_STEP_LOG_CHANNEL = "experiment-local-step-log-info";


    @Inject
    ProjectFederatedExperimentBO federatedExperimentBO;

    @Inject
    ProjectLocalExperimentBO localExperimentBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    @Channel(EXPERIMENT_LOCAL_CHANNEL)
    Multi<ProjectLocalExperimentDTO> localInfo;

    @Inject
    @Channel(EXPERIMENT_FED_CHANNEL)
    Multi<ProjectFederatedExperimentDetailDTO> fedInfo;

    @Inject
    @Channel(EXPERIMENT_LOCAL_STEP_LOG_CHANNEL)
    Multi<RunMessageLogDTO> localStepLogInfo;

    @Override
    public List<ProjectFederatedExperimentDTO> listFederated(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedExperimentBO.getAllByProject(projectId, keycloakId);
    }

    @Override
    @Transactional
    public Response createFederated(Long projectId, CreateProjectFederatedExperimentDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        Set<String> roles = userIdentity.getRoles();
        ProjectFederatedExperimentDTO createdProject = federatedExperimentBO.create(projectId, createDTO, keycloakId, roles);
        return Response.status(Response.Status.CREATED).entity(createdProject).build();
    }

    @Override
    public Multi<ProjectFederatedExperimentDetailDTO> retrieveFederated(Long projectId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom()
                .item(() -> federatedExperimentBO.getById(projectId, id, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure(NotFoundException.class).recoverWithNull()
                .toMulti()
                .onItem().transformToMultiAndConcatenate(federated ->
                        federated == null ? Multi.createFrom().empty() : Multi.createBy().concatenating().streams(
                                Multi.createFrom().item(federated),
                                fedInfo.filter(e -> e.getId().equals(federated.getId()))
                        )
                );
    }

    @Override
    @Transactional
    public ProjectFederatedExperimentDetailDTO startFederatedLearning(Long id, Long eId) {
        String keycloakId = userIdentity.getKeycloakId();
        ProjectFederatedExperimentDetailDTO startedProject = federatedExperimentBO.startLearning(id, eId, keycloakId);
        return startedProject;
    }

    @Override
    @Transactional
    public ProjectFederatedExperimentDetailDTO stopFederatedLearning(Long id, Long eId) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedExperimentBO.stopLearning(id, eId, keycloakId);
    }

    @Override
    public ProjectFederatedExperimentStepDTO getFederatedStepDetailUpdates(Long id, Long experimentId, Long stepId) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedExperimentBO.getStepById(id, experimentId, stepId, keycloakId);
    }

    @Override
    public List<ProjectLocalExperimentDTO> listLocal(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        return localExperimentBO.getAllByProject(projectId, keycloakId);
    }

    @Override
    @Transactional
    public Response createLocal(Long projectId, CreateProjectLocalExperimentDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        ProjectLocalExperimentDTO createdProject = localExperimentBO.create(projectId, createDTO, keycloakId);
        return Response.status(Response.Status.CREATED).entity(createdProject).build();
    }

    @Override
    public Multi<ProjectLocalExperimentDTO> retrieveLocal(Long projectId, Long eId) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom()
                .item(() -> localExperimentBO.getById(projectId, eId, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .toMulti()
                .onItem().transformToMultiAndConcatenate(localExperimentDTO ->
                        Multi.createBy().concatenating().streams(
                                Multi.createFrom().item(localExperimentDTO),
                                localInfo.filter(e -> e.getId().equals(localExperimentDTO.getId()))
                        )
                );
    }

    @Override
    @Transactional
    public ProjectLocalExperimentDTO startLocal(Long id, Long eId) {
        String keycloakId = userIdentity.getKeycloakId();
        return localExperimentBO.startLocal(id, eId, keycloakId);
    }

    @Override
    @Transactional
    public ProjectLocalExperimentDTO stopLocal(Long id, Long eId) {
        String keycloakId = userIdentity.getKeycloakId();
        return localExperimentBO.stopLocal(id, eId, keycloakId);
    }

    @Override
    @Transactional
    public Response createLocalTest(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        ProjectLocalExperimentDTO createdProject = localExperimentBO.createTestAndStart(projectId, keycloakId);
        return Response.status(Response.Status.CREATED).entity(createdProject).build();
    }

    @Override
    public Multi<ProjectLocalExperimentDTO> retrieveLocalTest(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom()
                .item(() -> {
                    try {
                        return localExperimentBO.getTestById(projectId, keycloakId);
                    } catch (NotFoundException e) {
                        return null;
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .toMulti()
                .onItem().transformToMultiAndConcatenate(localExperimentDTO -> {
                    if (localExperimentDTO == null) {
                        return Multi.createFrom().empty();
                    }
                    return Multi.createBy().concatenating().streams(
                            Multi.createFrom().item(localExperimentDTO),
                            localInfo.filter(e -> e.getId().equals(localExperimentDTO.getId()))
                    );
                });
    }

    @Override
    public Multi<RunMessageLogDTO> listLocalLogMessages(Long id, Long experimentId, Long stepId) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom()
                .item(() -> localExperimentBO.getStepById(id, experimentId, stepId, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure(jakarta.ws.rs.NotFoundException.class).recoverWithNull()
                .toMulti()
                .onItem().transformToMultiAndConcatenate(step -> {
                    if (step == null) {
                        return Multi.createFrom().empty();
                    }
                    return localStepLogInfo.filter(l -> l.getRunId().equals(step.getId()));
                });
    }

    @Override
    public ProjectLocalExperimentStepDetailDTO getLocalStepDetailUpdates(Long id, Long experimentId, Long stepId) {
        String keycloakId = userIdentity.getKeycloakId();
        return localExperimentBO.getStepById(id, experimentId, stepId, keycloakId);
    }

}
