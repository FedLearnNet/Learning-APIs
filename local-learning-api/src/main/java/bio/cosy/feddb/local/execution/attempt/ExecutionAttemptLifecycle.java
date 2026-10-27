package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningRunBO;
import bio.cosy.feddb.local.execution.message.ExecutionMessageAO;
import bio.cosy.feddb.local.execution.message.ExecutionMessageEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class ExecutionAttemptLifecycle extends BaseAttemptLifecycle<ExecutionAttemptEntity, ExecutionAttemptAO,
        ExecutionMessageEntity, ExecutionMessageAO> {

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    @Inject
    PatientToolBO patientToolBO;

    public ExecutionAttemptLifecycle() {
        super(attempt -> {
            ExecutionMessageEntity message = new ExecutionMessageEntity();
            message.setAttempt(attempt);
            return message;
        });
    }

    /**
     * Whether the app starts as soon as it connects. A step across clinics starts once all of them are ready and
     * a transformer session sends its batches itself; such an app may also drop and connect again.
     */
    @Transactional
    public boolean startsOnConnectTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId)
                .map(attempt -> attempt.getStep().getRun().getMode() != ExecutionMode.FEDERATED
                        && !connectorAppRunBO.isSession(attempt.getStep().getRun().getId()))
                .orElse(true);
    }

    /** An app that waits to be started connected; the step across clinics then counts it as ready. */
    @Transactional
    public void markConnectedTransactional(Long attemptId) {
        UpdateRunDTO update = new UpdateRunDTO();
        update.setStatus(RunStatusTypes.STARTED);
        updateTransactional(attemptId, update);
    }

    @Transactional
    public Optional<ExecutionPurpose> purposeOfTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId).map(attempt -> attempt.getStep().getRun().getPurpose());
    }

    /**
     * Keeps what a patient tool wrote before it counts as finished, so whoever waits for it reads it right away.
     */
    @Override
    protected void beforeFinish(Long attemptId, FinishRunDTO finish) {
        boolean finished = finish.getError() == null && finish.getStatus() != RunStatusTypes.ERROR
                && finish.getStatus() != RunStatusTypes.STOPPED;
        if (finished) {
            try {
                patientToolBO.keepOutputs(attemptId);
            } catch (RuntimeException e) {
                Log.errorf(e, "Could not keep the outputs of attempt %d", attemptId);
                finish.setStatus(RunStatusTypes.ERROR);
                finish.setError("The app finished but its outputs could not be kept: " + e.getMessage());
            }
        }
    }

    // An app of a training reads the clinic's data and learns with the other clinics through the relay.
    @Override
    protected void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        super.customizeStartup(attempt, start);
        if (attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TRAINING) {
            federatedLearningRunBO.customizeStartup(attempt, start);
        }
    }
}
