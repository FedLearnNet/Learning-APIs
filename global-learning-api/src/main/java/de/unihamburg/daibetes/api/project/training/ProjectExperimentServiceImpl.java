package de.unihamburg.daibetes.api.project.training;

import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.project.training.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingBO;
import de.unihamburg.daibetes.api.project.training.platform.CreateProjectLocalExperimentDTO;
import de.unihamburg.daibetes.api.project.training.platform.PlatformTrainingBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;

@ApplicationScoped
public class ProjectExperimentServiceImpl implements ProjectExperimentService {

    public static final String EXPERIMENT_CHANNEL = "experiment-info";

    @Inject
    FederatedTrainingBO federatedTrainingBO;

    @Inject
    PlatformTrainingBO platformTrainingBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    @Channel(EXPERIMENT_CHANNEL)
    Multi<ExecutionRunDetailDTO> runs;

    @Override
    public List<ExecutionRunDetailDTO> listFederated(Long projectId) {
        return federatedTrainingBO.listTransactional(projectId, userIdentity.getKeycloakId());
    }

    @Override
    public Response createFederated(Long projectId, CreateProjectFederatedExperimentDTO createDTO) {
        ExecutionRunDetailDTO created = federatedTrainingBO.createTransactional(projectId, createDTO, userIdentity.getKeycloakId(), userIdentity.getRoles());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Override
    public Multi<ExecutionRunDetailDTO> retrieveFederated(Long projectId, Long runId) {
        String keycloakId = userIdentity.getKeycloakId();
        return follow(Uni.createFrom().item(() -> federatedTrainingBO.findRunTransactional(projectId, runId, keycloakId)));
    }

    @Override
    public ExecutionRunDetailDTO startFederated(Long projectId, Long runId) {
        return federatedTrainingBO.startTransactional(projectId, runId, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO stopFederated(Long projectId, Long runId) {
        return federatedTrainingBO.stopTransactional(projectId, runId, userIdentity.getKeycloakId());
    }

    @Override
    public List<ExecutionRunDetailDTO> listLocal(Long projectId) {
        return platformTrainingBO.listTransactional(projectId, userIdentity.getKeycloakId());
    }

    @Override
    public Response createLocal(Long projectId, CreateProjectLocalExperimentDTO createDTO) {
        ExecutionRunDetailDTO created = platformTrainingBO.createTransactional(projectId, createDTO, userIdentity.getKeycloakId());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Override
    public Multi<ExecutionRunDetailDTO> retrieveLocal(Long projectId, Long runId) {
        String keycloakId = userIdentity.getKeycloakId();
        return follow(Uni.createFrom().item(() -> platformTrainingBO.findRunTransactional(projectId, runId, keycloakId, false)));
    }

    @Override
    public ExecutionRunDetailDTO retrieveLocalDetail(Long projectId, Long runId) {
        return platformTrainingBO.findRunTransactional(projectId, runId, userIdentity.getKeycloakId(), true);
    }

    @Override
    public ExecutionRunDetailDTO startLocal(Long projectId, Long runId) {
        return platformTrainingBO.startTransactional(projectId, runId, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO stopLocal(Long projectId, Long runId) {
        return platformTrainingBO.stopTransactional(projectId, runId, userIdentity.getKeycloakId());
    }

    @Override
    public Response createLocalTest(Long projectId) {
        ExecutionRunDetailDTO created = platformTrainingBO.createTestAndStartTransactional(projectId, userIdentity.getKeycloakId());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Override
    public Multi<ExecutionRunDetailDTO> retrieveLocalTest(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        return follow(Uni.createFrom().item(() -> platformTrainingBO.findTestTransactional(projectId, keycloakId)));
    }

    // The training now, then every change of it; nothing for a project never tested.
    private Multi<ExecutionRunDetailDTO> follow(Uni<ExecutionRunDetailDTO> current) {
        return current.runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .toMulti()
                .onItem().transformToMultiAndConcatenate(run -> Multi.createBy().concatenating().streams(
                        Multi.createFrom().item(run),
                        runs.filter(changed -> changed.getId().equals(run.getId()))));
    }
}
