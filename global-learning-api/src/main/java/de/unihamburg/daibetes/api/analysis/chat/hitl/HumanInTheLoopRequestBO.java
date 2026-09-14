package de.unihamburg.daibetes.api.analysis.chat.hitl;

import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.agent.anlysis.PlanState;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageAO;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;


@ApplicationScoped
public class HumanInTheLoopRequestBO extends BaseBo<HumanInTheLoopRequestDTO, HumanInTheLoopRequestEntity, HumanInTheLoopRequestAO, HumanInTheLoopRequestMapper> {

    @Inject
    DataAnalysisChatMessageAO dataAnalysisChatMessageAO;

    @Transactional
    public Optional<HumanInTheLoopRequestDTO> findPendingByDataAnalysisTransactional(Long id, String keycloakId) {
        return ao.findPendingByWorkflow(id, keycloakId).map(mapper::entityToDto);
    }

    @Transactional
    public void setAnsweredTransactional(HumanInTheLoopRequestDTO dto, String answer) {
        HumanInTheLoopRequestEntity entity = ao.findById(dto.getId());
        entity.setAnswer(answer);
        entity.setIsAnswered(true);
        dto.setAnswer(answer);
        ao.persist(entity);
    }

    @Transactional
    public void createTransactional(String question, ModelWorkflowChatMessageDTO message, PlanState state) {
        DataAnalysisChatMessageEntity messageEntity = dataAnalysisChatMessageAO.findById(message.getId());
        HumanInTheLoopRequestEntity entity = new HumanInTheLoopRequestEntity();
        entity.setRequest(question);
        entity.setIsAnswered(false);
        entity.setMessage(messageEntity);
        entity.setState(state);
        ao.persist(entity);
        mapper.entityToDto(entity);
    }
}
