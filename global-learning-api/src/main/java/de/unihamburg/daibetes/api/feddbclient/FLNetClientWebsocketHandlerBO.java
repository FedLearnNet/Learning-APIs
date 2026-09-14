package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.socket.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.RunMetricsRequestBO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantBO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.query.response.QueryResponseBO;
import de.unihamburg.daibetes.api.query.statistics.DataStatisticsResponseBO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FLNetClientWebsocketHandlerBO {

    @Inject
    QueryResponseBO queryResponseBO;

    @Inject
    DataStatisticsResponseBO dataStatisticsResponseBO;

    @Inject
    RunMetricsRequestBO runMetricsRequestBO;

    @Inject
    ProjectFederatedExperimentBO projectFederatedExperimentBO;

    @Inject
    ProjectFederatedExperimentParticipantBO projectFederatedExperimentParticipantBO;

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @LoggerName("FLNetClientWebsocketHandler")
    Logger logger;

    @Transactional
    public <T> void handle(FedDBClientDataDTO<T> m, String connectionId) {
        ObjectMapper objectMapper = new ObjectMapper();

        if (m.getMessageType().equals(FedDBClientTypeEnum.EXISTING_QUERY)) {
            QueryClientResponseDTO query = objectMapper.convertValue(m.getMessage(), QueryClientResponseDTO.class);
            Log.infof("Received query response from %s", query);
            queryResponseBO.saveResponse(query);
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.DATA_STATISTICS)) {
            DataStatisticsResponseClientDTO query = objectMapper.convertValue(m.getMessage(), DataStatisticsResponseClientDTO.class);
            dataStatisticsResponseBO.saveResponse(query);
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.LEARNING_QUERY)) {
            LearningQueryClientResponseDTO query = objectMapper.convertValue(m.getMessage(), LearningQueryClientResponseDTO.class);
            projectFederatedExperimentBO.updateCount(
                    query.getGlobalFLExperimentUniqueId(),
                    query.getCount(),
                    query.getUniqueRandomClinicId(),
                    query.getModelCanBePublic()
            );
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.UPDATE_LEARNING)) {
            LearningClientSyncResponseDTO response = objectMapper.convertValue(m.getMessage(), LearningClientSyncResponseDTO.class);
            ProjectFederatedExperimentParticipantEntity participant = projectFederatedExperimentParticipantBO.updateStatus(response);
            if (participant != null) {
                projectFederatedExperimentBO.updateStepAndNotify(participant);
            }
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.CURRENT_LEARNINGS)) {
            CurrentLearningClientSyncResponseDTO response = objectMapper.convertValue(m.getMessage(), CurrentLearningClientSyncResponseDTO.class);
            broadcastBO.syncLearningConnection(connectionId, response);
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.RUN_METRICS)) {
            RunMetricsResponseClientDTO response = objectMapper.convertValue(m.getMessage(), RunMetricsResponseClientDTO.class);
            runMetricsRequestBO.saveResponse(response);
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.NO_RESPONSE)) {
            logger.info("No response received from client: " + connectionId);
        }

        if (m.getMessageType().equals(FedDBClientTypeEnum.ERROR)) {
            logger.warn("Error received from client: " + connectionId);
        }
    }
}
