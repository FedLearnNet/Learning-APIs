package bio.cosy.feddb.local.api.learning.project.run.federated;

import bio.cosy.feddb.core.api.socket.FlowerSupernodeKeyRequestDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeKeyDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeRegisteredDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passes a clinic's Flower SuperNode registration through this clinic: the app's public key goes up to the
 * global server, the platform aggregator's answer comes back down to the same attempt. Nothing is persisted -
 * the app re-sends its key under the same request id until it gets an answer, so a lost message or a restart
 * here only costs one retry. Unlike FL-Net's relay certificate (see FederatedLearningProgressBO), there is no
 * scheduled retry on this side.
 */
@ApplicationScoped
public class FlowerSupernodeRegistrationBO {

    // request id -> attempt whose app asked, until the answer arrives
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    LearningProjectAO projectAO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    ExecutionRunBO executionRunBO;

    /** Forwards the app's key to the global server, for the training and workflow step the attempt runs. */
    @Transactional
    public void forwardKeyTransactional(Long attemptId, FlowerSupernodeKeyDTO key) {
        if (key == null || key.getRequestId() == null || key.getPublicKey() == null) {
            Log.warnf("Ignoring an incomplete Flower SuperNode key from attempt %d", attemptId);
            return;
        }
        Optional<FlowerSupernodeKeyRequestDTO> request = requestFor(attemptId, key);
        if (request.isEmpty()) {
            Log.warnf("Ignoring a Flower SuperNode key from attempt %d, it does not run a federated training", attemptId);
            return;
        }
        pending.put(key.getRequestId(), attemptId);
        websocketSender.sendFlowerSupernodeKey(request.get());
        Log.infof("Asked the platform to register the Flower SuperNode of attempt %d", attemptId);
    }

    /** Hands the platform's answer to the attempt that asked. */
    public void forwardAnswer(FlowerSupernodeRegisteredDTO registered) {
        Long attemptId = registered == null || registered.getRequestId() == null ? null : pending.remove(registered.getRequestId());
        if (attemptId == null) {
            Log.warnf("Ignoring a Flower SuperNode registration answer nobody here is waiting for");
            return;
        }
        executionRunBO.sendToApp(attemptId,
                new AppMessageWrapperDTO<>(AppMessageTypeEnum.FLOWER_SUPERNODE_REGISTERED, registered, AppRunTypeEnum.FEDERATED_RUN));
    }

    private Optional<FlowerSupernodeKeyRequestDTO> requestFor(Long attemptId, FlowerSupernodeKeyDTO key) {
        ExecutionAttemptEntity attempt = attemptAO.findById(attemptId);
        if (attempt == null) {
            return Optional.empty();
        }
        ExecutionRunStepEntity step = attempt.getStep();
        ExecutionRunEntity run = step.getRun();
        LearningProjectEntity project = projectAO.findByRunId(run.getId());
        if (project == null || project.getRequest() == null || step.getWorkflowNode() == null) {
            return Optional.empty();
        }
        return Optional.of(new FlowerSupernodeKeyRequestDTO(key.getRequestId(), project.getRequest().getGlobalExperimentUniqueId(),
                run.getParticipantId(), step.getWorkflowNode().getNodeId(), key.getPublicKey()));
    }
}
