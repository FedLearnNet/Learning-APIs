package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStopLearningRequestDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.run.WorkflowExecutionBO;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class FederatedLearningCleanupBO {

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    FederatedLearningRequestBO requestBO;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    public void stopTraining(ExecutionRunEntity run) {
        stopControllerRuns(run);
        workflowExecutionBO.stopTransactional(run.getId());
    }

    public void finishTraining(ExecutionRunEntity run, String globalId) {
        finishRun(run, RunStatusTypes.FINISHED, null);
        requestBO.finishLearning(globalId);
    }

    public void failTraining(ExecutionRunEntity run, ExecutionFailure failure, String globalId) {
        stopControllerRuns(run);
        finishRun(run, RunStatusTypes.ERROR, failure);
        requestBO.stopLearning(globalId);
    }

    // Never throws: a controller that cannot be reached must not break the training, its run stays open.
    public void stopController(ExecutionFederationEntity federation) {
        if (federation == null || federation.getConfig() == null || federation.getRelayCsr() == null
                || Boolean.TRUE.equals(federation.getRelayStopped())) {
            return;
        }
        int status;
        try (Response response = controllerService.stopLearning(new ControllerStopLearningRequestDTO(
                federation.getConfig().getChannel(), federation.getConfig().getClientId()))) {
            status = response.getStatus();
        } catch (WebApplicationException e) {
            status = e.getResponse().getStatus();
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not stop the controller run of step %d", federation.getStep().getId());
            return;
        }
        // 404: the controller does not know the run anymore, e.g. after it restarted.
        if (status == Response.Status.OK.getStatusCode() || status == Response.Status.NOT_FOUND.getStatusCode()) {
            federation.setRelayStopped(true);
        } else {
            Log.warnf("Controller answered %d when stopping the run of step %d", status, federation.getStep().getId());
        }
    }

    private void stopControllerRuns(ExecutionRunEntity run) {
        federationAO.listOpenControllerRuns(run.getId()).forEach(this::stopController);
    }

    private void finishRun(ExecutionRunEntity run, RunStatusTypes status, ExecutionFailure failure) {
        if (run.hasEnded()) {
            return;
        }
        run.end(status, failure);
        events.runFinished(run.getId(), status);
    }
}
