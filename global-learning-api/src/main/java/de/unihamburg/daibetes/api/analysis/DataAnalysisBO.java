package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisExecutionBO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAllowedException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


@ApplicationScoped
public class DataAnalysisBO extends BaseBo<ModelWorkflowDTO, DataAnalysisEntity, DataAnalysisAO, DataAnalysisMapper> {

    @Inject
    DataAnalysisExecutionBO executionBO;

    @Inject
    DataAnalysisRunResultBO runResultBO;

    public List<ModelWorkflowDTO> list(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAll(keycloakId));
    }

    /** The analysis with its timeline: chat messages and the runs of the execution. */
    public DataAnalysisDetailDTO findById(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .map(mapper::entityToDetail)
                .map(detail -> withTimeline(detail, runResultBO.runsOfAnalysis(detail.getId()).stream()
                        .map(run -> wrap(run, ModelWorkflowChatMessageType.RUN))))
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    /** The analysis for its report, runs as results; flat lists every workflow step on its own. */
    public DataAnalysisDetailDTO findForReport(Long id, String keycloakId, boolean flat) {
        return ao.findById(keycloakId, id)
                .map(entity -> flat ? mapper.entityToDetailFlat(entity) : mapper.entityToDetail(entity))
                .map(detail -> withTimeline(detail, runResultBO.resultsOfAnalysis(detail.getId(), flat).stream()
                        .map(result -> wrap(result, result.getRunMode() == DataAnalysisRunModesEnum.WORKFLOW
                                ? ModelWorkflowChatMessageType.WORKFLOW_PREDICTION
                                : ModelWorkflowChatMessageType.PREDICTION))))
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    private static DataAnalysisDetailDTO withTimeline(DataAnalysisDetailDTO detail, Stream<DataAnalysisChatWrapperDTO> runs) {
        List<DataAnalysisChatWrapperDTO> messages = new ArrayList<>(detail.getMessages() != null ? detail.getMessages() : List.of());
        runs.forEach(messages::add);
        messages.sort(Comparator.comparing((DataAnalysisChatWrapperDTO wrapper) -> wrapper.getMessage().getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        detail.setMessages(messages);
        return detail;
    }

    private static <T extends BaseDTO> DataAnalysisChatWrapperDTO wrap(T message, ModelWorkflowChatMessageType type) {
        DataAnalysisChatWrapperDTO<T> wrapper = new DataAnalysisChatWrapperDTO<>();
        wrapper.setMessage(message);
        wrapper.setType(type);
        return wrapper;
    }

    public DataAnalysisEntity findEntityById(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    public void delete(Long id, String keycloakId) {
        ao.deleteById(keycloakId, id);
    }

    public DataAnalysisDetailDTO createWorkflow(String keycloakId, String name) {
        DataAnalysisEntity workflow = new DataAnalysisEntity();
        workflow.setKeycloakId(keycloakId);
        workflow.setName(name);
        ao.persist(workflow);
        return mapper.entityToDetail(workflow);
    }

    @Transactional
    public void setLLMSummaryTransactional(Long id, String summary, String keycloakId) {
        Optional<DataAnalysisEntity> workflowOptional = ao.findById(keycloakId, id);
        if (workflowOptional.isEmpty()) {
            Log.errorf("Workflow with id %d not found for keycloakId %s", id, keycloakId);
            return;
        }
        DataAnalysisEntity workflow = workflowOptional.get();
        workflow.setLlmSummary(summary);
        ao.persist(workflow);
    }

    public void invalidateLlmSummary(Long id) {
        Optional<DataAnalysisEntity> workflowOptional = ao.findByIdOptional(id);
        if (workflowOptional.isEmpty()) {
            Log.errorf("Workflow with id %d not found", id);
            return;
        }
        DataAnalysisEntity workflow = workflowOptional.get();
        if (workflow.getLlmSummary() == null) {
            return;
        }
        workflow.setLlmSummary(null);
        ao.persist(workflow);
    }

    @Transactional
    public Optional<String> getLLSummaryOptionalTransactional(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .map(DataAnalysisEntity::getLlmSummary);

    }

    public DataAnalysisRunDTO stopRun(Long dataAnalysisId, Long runId, String keycloakId) {
        return executionBO.stop(dataAnalysisId, runId, keycloakId);
    }

    public void deleteRun(Long dataAnalysisId, Long id, Long workflowId, String keycloakId) {
        executionBO.delete(dataAnalysisId, workflowId != null ? workflowId : id, keycloakId);
    }
}
