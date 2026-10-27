package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.helper.NodeHyperParams;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.message.BaseExecutionMessageAO;
import bio.cosy.feddb.core.execution.message.BaseExecutionMessageEntity;
import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.message.RunMessageTypes;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Applies what an app reports about its attempt: running, logs and metrics, finished. Open statuses only
 * move forward, so a late or repeated message cannot undo a later state; a final status hands the
 * attempt back to its step through the attempt finished event.
 */
public abstract class BaseAttemptLifecycle<
        Attempt extends BaseExecutionAttemptEntity<?, Message>,
        AttemptAO extends BaseExecutionAttemptAO<Attempt>,
        Message extends BaseExecutionMessageEntity,
        MessageAO extends BaseExecutionMessageAO<Message>
        > {

    // Order an open attempt moves through; a final status ends it.
    private static final List<RunStatusTypes> OPEN = List.of(RunStatusTypes.PENDING, RunStatusTypes.INITIALIZED,
            RunStatusTypes.STARTED, RunStatusTypes.RUNNING);

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    public AttemptAO attemptAO;

    @Inject
    public MessageAO messageAO;

    // Creates a message already linked to its attempt.
    private final Function<Attempt, Message> newMessage;

    protected BaseAttemptLifecycle(Function<Attempt, Message> newMessage) {
        this.newMessage = newMessage;
    }

    @Transactional
    public boolean checkIfEndedTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId)
                .map(attempt -> attempt.hasEnded())
                .orElse(true);
    }

    /** What the app runs: its node's hyper parameters with the trial's on top. */
    @Transactional
    public StartRunDTO startupOf(Long attemptId) {
        Attempt attempt = attemptAO.findByIdOptional(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt " + attemptId + " not found"));
        LinkedHashMap<String, Object> hyperParams = NodeHyperParams.of(objectMapper, attempt.getStep().getWorkflowNode());
        if (attempt.getParams() != null) {
            hyperParams.putAll(attempt.getParams());
        }
        StartRunDTO start = new StartRunDTO();
        start.setId(attemptId);
        start.setStatus(RunStatusTypes.PENDING);
        start.setHyperParams(hyperParams);
        start.setSupportFederatedLearning(attempt.getStep().getWorkflowNode() != null
                && attempt.getStep().getWorkflowNode().isSupportsFederatedLearning());
        // A training keeps the model the app trains.
        start.setTrainable(attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TRAINING);
        customizeStartup(attempt, start);
        return start;
    }

    /**
     * Hands the step's inputs to the app as input data. Purposes override it to add what only they know,
     * e.g. download links of input files.
     */
    protected void customizeStartup(Attempt attempt, StartRunDTO start) {
        LinkedHashMap<String, Object> inputs = attempt.getStep().getInputs();
        start.setInputData(inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>());
        LinkedHashMap<String, String> inputFilePaths = attempt.getStep().getInputFilePaths();
        start.setInputFilePaths(inputFilePaths != null ? new LinkedHashMap<>(inputFilePaths) : new LinkedHashMap<>());
    }

    @Transactional
    public void markRunning(Long attemptId) {
        withAttempt(attemptId, attempt -> moveTo(attempt, RunStatusTypes.RUNNING));
    }

    @Transactional
    public void update(Long attemptId, UpdateRunDTO update) {
        withAttempt(attemptId, attempt -> {
            if (update.getError() != null || update.getStatus() == RunStatusTypes.ERROR) {
                end(attempt, RunStatusTypes.ERROR, appFailure(update.getError()));
                return;
            }
            if (update.getProgress() != null) {
                attempt.setProgress(update.getProgress());
            }
            if (update.getStatus() != null && RunStatusTypes.isFinalStatus(update.getStatus())) {
                end(attempt, update.getStatus(), null);
            } else if (update.getStatus() != null) {
                moveTo(attempt, update.getStatus());
            }
        });
    }

    @Transactional
    public void finish(Long attemptId, FinishRunDTO finish) {
        withAttempt(attemptId, attempt -> {
            attempt.setMeta(finish.getMeta());
            if (finish.getError() != null || finish.getStatus() == RunStatusTypes.ERROR) {
                end(attempt, RunStatusTypes.ERROR, appFailure(finish.getError()));
            } else {
                end(attempt, finish.getStatus() == RunStatusTypes.STOPPED ? RunStatusTypes.STOPPED : RunStatusTypes.FINISHED, null);
            }
        });
    }

    @Transactional
    public void log(Long attemptId, ExecutionMessageLogDTO log) {
        withAttempt(attemptId, attempt -> store(attempt, RunMessageTypes.LOG, log));
    }

    @Transactional
    public void metric(Long attemptId, ExecutionMessageMetricDTO metric) {
        withAttempt(attemptId, attempt -> {
            Message message = store(attempt, RunMessageTypes.METRIC, metric);
            HyperParamSearchDTO search = attempt.getStep().getSearch();
            // The latest value of the search metric is the one its trial is ranked by.
            if (search != null && metric.getMetric() != null && metric.getMetric().equals(search.getMetric())) {
                attempt.setScoreMessage(message);
            }
        });
    }

    @Transactional
    public void round(Long attemptId, ExecutionMessageRoundDTO round) {
        withAttempt(attemptId, attempt -> store(attempt, RunMessageTypes.ROUND, round));
    }

    // Every applied report is announced, so views of the attempt can refresh.
    private void withAttempt(Long attemptId, Consumer<Attempt> action) {
        attemptAO.findByIdWrite(attemptId).ifPresentOrElse(attempt -> {
                    action.accept(attempt);
                    events.attemptUpdated(attemptId);
                },
                () -> Log.warnf("App reported on unknown attempt %d", attemptId));
    }

    private void moveTo(Attempt attempt, RunStatusTypes next) {
        int current = OPEN.indexOf(attempt.getStatus());
        if (current >= 0 && OPEN.indexOf(next) > current) {
            attempt.setStatus(next);
        }
    }

    private void end(Attempt attempt, RunStatusTypes status, ExecutionFailure failure) {
        if (attempt.hasEnded()) {
            Log.debugf("Ignoring end of attempt %d, it already ended as %s", attempt.getId(), attempt.getStatus());
            return;
        }
        attempt.end(status, failure);
        events.attemptFinished(attempt);
        Log.infof("Attempt %d finished as %s", attempt.getId(), status);
    }

    private Message store(Attempt attempt, RunMessageTypes type, ExecutionMessageDTO payload) {
        payload.setRunId(attempt.getId());
        payload.setType(type);
        Message message = newMessage.apply(attempt);
        message.setType(type);
        message.setProcess(payload.getProcess());
        message.setPayload(payload);
        messageAO.persist(message);
        return message;
    }

    private ExecutionFailure appFailure(String error) {
        return new ExecutionFailure(FailureCategory.TOOL, error != null ? error : "App reported an error");
    }
}
