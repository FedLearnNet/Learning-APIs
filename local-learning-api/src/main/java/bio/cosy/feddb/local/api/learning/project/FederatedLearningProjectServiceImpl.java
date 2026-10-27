package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.io.File;

@ApplicationScoped
public class FederatedLearningProjectServiceImpl implements FederatedLearningProjectService {
    @Inject
    FederatedLearningProjectBO bo;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

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
    public ExecutionRunDTO retrieveRun(Long id) {
        return runBO.findRunTransactional(runIdOf(id), true);
    }

    @Override
    public Multi<ExecutionRunDTO> followRun(Long id) {
        return Uni.createFrom().item(() -> runIdOf(id))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .toMulti()
                .onItem().transformToMultiAndConcatenate(runBO::subscribe);
    }

    // Only for members of every cohort the training reads.
    private Long runIdOf(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.getCohortIds(id).forEach(cohortId -> cohortMemberAuthBO.checkForMember(cohortId, keycloakId));
        FederatedLearningProjectDTO project = bo.getProjectDetailById(id);
        if (project.getRun() == null) {
            throw new NotFoundException("Training " + id + " has no run yet");
        }
        return project.getRun().getId();
    }
}
