package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDetailDTO;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.io.File;

@ApplicationScoped
public class FederatedLearningProjectServiceImpl implements FederatedLearningProjectService {
    public static final String TRAINING_STEP_LOG_CHANNEL = "training-step-log-info";

    @Inject
    FederatedLearningProjectBO bo;

    @Inject
    FederatedLearningExperimentStepBO stepBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    @Channel(TRAINING_STEP_LOG_CHANNEL)
    Multi<RunMessageLogDTO> localStepLogInfo;

    @Override
    public PagedResponse<FederatedLearningProjectDTO> list(int pageIndex, int size) {
        Page page = Page.of(pageIndex, size);
        PagedResponse<FederatedLearningProjectDTO> response = bo.list(page);
        String keycloakId = userIdentity.getKeycloakId();
        response.setResults(response.getResults().stream()
                .filter(dto -> bo.getCohortIds(dto.getProject().getId()).stream()
                        .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .toList());
        response.setTotalCount(response.getResults().size());
        return response;
    }

    @Override
    public FederatedLearningProjectDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        return bo.getProjectDetailById(id);
    }

    @Override
    @Transactional
    public Response downloadFile(Long projectId) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(projectId).forEach(cohortId -> cohortMemberAuthBO.checkForEditPatients(cohortId, keycloakId));
        File file = bo.exportForLearning(projectId).toFile();
        if (file.exists()) {
            return Response.ok(file)
                    .header("Content-Disposition", "attachment; filename=\"export_" + projectId + ".csv\"")
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Project not found: " + projectId)
                    .build();
        }
    }

    @Override
    public Multi<RunMessageLogDTO> listLogMessages(Long id, Long stepId) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom()
                .item(() -> {
                    bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
                    return stepBO.getById(id, stepId);
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure(NotFoundException.class).recoverWithNull()
                .toMulti()
                .onItem().transformToMultiAndConcatenate(step -> {
                    if (step == null) {
                        return Multi.createFrom().empty();
                    }
                    return localStepLogInfo.filter(l -> l.getRunId().equals(step.getId()));
                });
    }

    @Override
    public FederatedLearningExperimentStepDetailDTO getStepDetailUpdates(Long id, Long stepId) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        return stepBO.getDetailById(id, stepId);
    }
}
