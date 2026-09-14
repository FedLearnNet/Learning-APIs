package de.unihamburg.daibetes.api.project.experiment.local.run;

import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProjectLocalExperimentBroadcastBO {
    @Inject
    OpenConnections connections;


    public void startStep(StartRunDTO startRunDTO, boolean isTraining) {

        AppMessageTypeEnum t = AppMessageTypeEnum.START_PREDICTION;
        if (isTraining) {
            t = AppMessageTypeEnum.START_RUN;
        }

        AppMessageWrapperDTO<StartRunDTO> message = new AppMessageWrapperDTO<>(t, startRunDTO, AppRunTypeEnum.PROJECT_RUN);
        sendMessageToRunId(message, startRunDTO.getId());
    }


    private void sendMessageToRunId(AppMessageWrapperDTO<?> message, Long runId) {
        Log.debugf("Sending message to runId %s: %s", runId, message);
        connections.findByEndpointId(ProjectLocalExperimentRunWebsocketService.class.getName())
                .forEach(c -> {
                    Long rId = Long.parseLong(c.pathParam("stepId"));
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
