package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.learning.project.run.LearningRunBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@ApplicationScoped
public class ExecutionAttemptServiceImpl implements ExecutionAttemptService {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    LearningRunBO learningRunBO;

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    public Response bootstrap(Long attemptId) {
        return Response.ok(Map.of("apiKey", lifecycle.bootstrapCredentialTransactional(attemptId)))
                .header("Cache-Control", "no-store").build();
    }

    public Response uploadOutput(Long attemptId, AppRunUploadData upload) {
        if (upload.getRunId() != null && !attemptId.equals(upload.getRunId())
                && !(upload.getRunId() < 0 && connectorAppRunBO.isSessionAttempt(attemptId))) {
            throw new ForbiddenException("Output belongs to another execution");
        }
        if (upload.getRunId() == null) {
            upload.setRunId(attemptId);
        }
        if (lifecycle.checkIfEndedTransactional(attemptId)) {
            throw new WebApplicationException("Execution has ended", 409);
        }
        ExecutionPurpose purpose = lifecycle.purposeOfTransactional(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt " + attemptId + " not found"));
        if (purpose == ExecutionPurpose.TRAINING) {
            learningRunBO.keepOutputsTransactional(attemptId, upload.getFilesNullsafe());
        } else {
            connectorAppRunBO.uploadOutput(attemptId, upload);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    public Response uploadModel(Long attemptId, ModelSubDataDTO model) {
        if (model.getRunId() != null && !attemptId.equals(model.getRunId())) {
            throw new ForbiddenException("Model belongs to another execution");
        }
        model.setRunId(attemptId);
        if (lifecycle.checkIfEndedTransactional(attemptId)) {
            throw new WebApplicationException("Execution has ended", 409);
        }
        if (lifecycle.purposeOfTransactional(attemptId).orElse(null) != ExecutionPurpose.TRAINING) {
            throw new ForbiddenException("Execution does not accept model uploads");
        }
        learningRunBO.uploadModelTransactional(attemptId, model);
        return Response.status(Response.Status.CREATED).build();
    }
}
