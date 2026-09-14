package de.unihamburg.daibetes.api.project.experiment.local.message;

import bio.cosy.feddb.core.api.run.message.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.List;

import static de.unihamburg.daibetes.api.project.experiment.ProjectExperimentServiceImpl.EXPERIMENT_LOCAL_STEP_LOG_CHANNEL;

@ApplicationScoped
public class ProjectLocalExperimentStepMessageBO extends BaseRunMessageBO<ProjectLocalExperimentStepMessageEntity, ProjectLocalExperimentStepMessageAO, ProjectLocalExperimentStepMessageMapper> {

    @Inject
    @Channel(EXPERIMENT_LOCAL_STEP_LOG_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<RunMessageLogDTO> infoEmitter;

    public List<RunMessageDTO> findByExperiment(Long experimentId) {
        return mapper.entitiesToDtos(ao.findByExperiment(experimentId));
    }

    public List<RunMessageLogDTO> findLogByStepId(Long stepId) {
        List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByStep(stepId, RunMessageTypes.LOG));
        return mapToLogs(dtos);
    }

    public List<RunMessageMetricDTO> findMetricByStepId(Long stepId) {
        List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByStep(stepId, RunMessageTypes.METRIC));
        return mapToMetrics(dtos);
    }

    public void logMessage(Long stepId, RunMessageLogDTO runMessage) {
        RunMessageLogDTO logged = create(runMessage, stepId);
        infoEmitter.send(logged);
    }

    public void logMessage(Long stepId, RunMessageMetricDTO runMetric) {
        create(runMetric, stepId);

    }
}
