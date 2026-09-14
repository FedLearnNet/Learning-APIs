package bio.cosy.feddb.local.api.eam;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class WebsocketSender {

    @Inject
    ClientManager client;


    public void sendRunningUpdate(String currentNodeId, String globalUqExperimentId, ProjectStatus stepStatus, String randomClinicId) {
        if (globalUqExperimentId == null) {
            Log.errorf("Global unique ExperimentId ID is null. Cannot send running update. (UPDATE_LEARNING Prepare)");
            return;
        }
        RunStatusTypes projectStatus = RunStatusTypes.RUNNING;
        sendRunningUpdate(currentNodeId, globalUqExperimentId, projectStatus, stepStatus, randomClinicId);
    }

    public void sendRunningUpdate(String currentNodeId, String globalUqExperimentId, RunStatusTypes projectStatus, ProjectStatus stepStatus, String randomClinicId) {
        if (globalUqExperimentId == null) {
            Log.errorf("Global unique ExperimentId ID is null. Cannot send running update. (UPDATE_LEARNING)");
            return;
        }
        if (randomClinicId == null) {
            Log.errorf("Random Clinic ID is null. Cannot send running update. (UPDATE_LEARNING)");
            return;
        }
        LearningClientSyncResponseDTO syncResponse = LearningClientSyncResponseDTO.createResponse(currentNodeId, globalUqExperimentId, projectStatus, stepStatus, randomClinicId);
        FedDBClientDataDTO<LearningClientSyncResponseDTO> response = new FedDBClientDataDTO<>();
        response.setMessageType(FedDBClientTypeEnum.UPDATE_LEARNING);
        response.setMessage(syncResponse);
        client.fireAndForget(response);
    }

    public void sendLearningClientResponse(int count, String globalFLExperimentUniqueId, String uniqueClintId, Boolean modelCanBePublic) {
        if (uniqueClintId == null) {
            Log.errorf("Random Clinic ID is null. Cannot send running update. (Learning Client Response)");
            return;
        }
        LearningQueryClientResponseDTO message = LearningQueryClientResponseDTO.createResponse(count, globalFLExperimentUniqueId, uniqueClintId);
        message.setModelCanBePublic(modelCanBePublic);
        FedDBClientDataDTO<LearningQueryClientResponseDTO> response = new FedDBClientDataDTO<>();
        response.setMessageType(FedDBClientTypeEnum.LEARNING_QUERY);
        response.setMessage(message);
        client.fireAndForget(response);
    }

    public void sendDataStatisticsClientResponse(DataStatisticsResponseClientDTO response) {
        if (response == null) {
            Log.errorf("DataStatisticsResponseDTO is null. Cannot send DataStatisticsResponseDTO.");
            return;
        }
        FedDBClientDataDTO<DataStatisticsResponseClientDTO> responseWrapper = new FedDBClientDataDTO<>();
        responseWrapper.setMessageType(FedDBClientTypeEnum.DATA_STATISTICS);
        responseWrapper.setMessage(response);
        client.fireAndForget(responseWrapper);
    }

    public void sendRunMetricsClientResponse(RunMetricsResponseClientDTO response) {
        if (response == null) {
            Log.errorf("RunMetricsResponseClientDTO is null. Cannot send metrics response.");
            return;
        }
        FedDBClientDataDTO<RunMetricsResponseClientDTO> responseWrapper = new FedDBClientDataDTO<>();
        responseWrapper.setMessageType(FedDBClientTypeEnum.RUN_METRICS);
        responseWrapper.setMessage(response);
        client.fireAndForget(responseWrapper);
    }

    public void sendCurrentLearnings(List<String> currentGlobalUqLearningIds) {
        CurrentLearningClientSyncResponseDTO syncResponse = CurrentLearningClientSyncResponseDTO.createResponse(currentGlobalUqLearningIds);
        FedDBClientDataDTO<CurrentLearningClientSyncResponseDTO> response = new FedDBClientDataDTO<>();
        response.setMessageType(FedDBClientTypeEnum.CURRENT_LEARNINGS);
        response.setMessage(syncResponse);
        client.fireAndForget(response);
    }

}
