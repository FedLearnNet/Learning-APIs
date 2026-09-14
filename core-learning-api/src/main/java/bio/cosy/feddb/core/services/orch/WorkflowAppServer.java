package bio.cosy.feddb.core.services.orch;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.base.BaseAppServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;


/**
 * WorkflowAppServer is an abstract class extending the functionality of BaseAppServer
 * to handle workflow-specific federated learning application server operations. This includes processing
 * various types of messages, such as those related to runs, metrics, updates, and logs.
 * <p>
 * This class defines abstract methods that subclasses must implement to provide specific
 * logic for starting, logging, and processing application workflows.
 * <p>
 * It also contains a concrete implementation for handling application messages
 * and invoking the appropriate procedures based on the message type.
 * <p>
 * This is the abstract Websocket for the fl-apps communication.
 */
public abstract class WorkflowAppServer extends BaseAppServer {

    protected abstract void logMetric(Long runId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType);

    protected <T> void handleAppMessages(AppMessageWrapperDTO<T> m, Long runId, AppRunTypeEnum runType) {
        ObjectMapper objectMapper = new ObjectMapper();

        switch (m.getType()) {
            case CLIENT_STARTED:
                break;
            case SEND_MODEL:
                Log.warn("Received model upload, use HTTP endpoint instead");
                break;
            case START_RUN:
                StartRunDTO startRunDTO = objectMapper.convertValue(m.getMessage(), StartRunDTO.class);
                startRun(runId, startRunDTO, runType);
                break;
            case UPDATE_RUN:
                UpdateRunDTO updateTest = objectMapper.convertValue(m.getMessage(), UpdateRunDTO.class);
                updateRun(runId, updateTest, runType);
                break;
            case FINISH_RUN:
                FinishRunDTO finishTest = objectMapper.convertValue(m.getMessage(), FinishRunDTO.class);
                finishRun(runId, finishTest, runType);
                break;
            case UPDATE_FEDERATED_RUN: {
                // Federated runs report their lifecycle with dedicated message types; previously these
                // fell through to default and were silently dropped, so app errors never reached the
                // step (and therefore never stopped the experiment globally).
                FederatedRunLifecycleDTO fed = objectMapper.convertValue(m.getMessage(), FederatedRunLifecycleDTO.class);
                RunStatusTypes fedStatus = fed.toRunStatus();
                if (fedStatus != null) {
                    UpdateRunDTO update = new UpdateRunDTO();
                    update.setRunId(fed.getId() != null ? fed.getId() : runId);
                    update.setStatus(fedStatus);
                    update.setError(fed.getError());
                    updateRun(runId, update, runType);
                }
                break;
            }
            case FINISH_FEDERATED_RUN: {
                FederatedRunLifecycleDTO fed = objectMapper.convertValue(m.getMessage(), FederatedRunLifecycleDTO.class);
                FinishRunDTO fedFinish = new FinishRunDTO();
                fedFinish.setRunId(fed.getId() != null ? fed.getId() : runId);
                fedFinish.setStatus(fed.toRunStatus());
                fedFinish.setError(fed.getError());
                finishRun(runId, fedFinish, runType);
                break;
            }
            case FEDERATED_PARTICIPANT_UPDATE:
            case FEDERATED_ROUND_MESSAGE:
                // Per-participant/round telemetry - informational only, no step-state change.
                Log.debugf("Federated telemetry for run %d: %s", runId, m.getType());
                break;
            case LOG_MESSAGE:
                RunMessageLogDTO runMessage = objectMapper.convertValue(m.getMessage(), RunMessageLogDTO.class);
                logMessage(runMessage.getRunId(), runMessage, runType);
                break;
            case LOG_METRIC:
                RunMessageMetricDTO runMetric = objectMapper.convertValue(m.getMessage(), RunMessageMetricDTO.class);
                logMetric(runMetric.getRunId(), runMetric, runType);
                break;
            default:
                //Log.warnf("Received unknown message type: " + m.getType());
                break;
        }

    }
}
