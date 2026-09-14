package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentWebsocketService;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PatientToolBroadcastBO {
    @Inject
    OpenConnections connections;

    @Transactional
    public void startStep(StartRunDTO run) {
        Log.info("Starting run for runId: " + run.getId());
        AppMessageWrapperDTO<StartRunDTO> message = new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, run, AppRunTypeEnum.FEDERATED_RUN);
        sendMessageToRunId(message, run.getId());
    }


    private void sendMessageToRunId(AppMessageWrapperDTO<?> message, Long runId) {
        Log.debugf("Sending message to runId %s: %s", runId, message);
        connections.findByEndpointId(FederatedLearningExperimentWebsocketService.class.getName())
                .forEach(c -> {
                    Long rId = Long.parseLong(c.pathParam("runId"));
                    if (rId.equals(runId)) {
                        try {
                            c.sendTextAndAwait(message);
                        } catch (Exception e) {
                            Log.errorf("Failed to send message to connection %s: %s", c.id(), e.getMessage());
                        }
                    }
                });
    }

}
