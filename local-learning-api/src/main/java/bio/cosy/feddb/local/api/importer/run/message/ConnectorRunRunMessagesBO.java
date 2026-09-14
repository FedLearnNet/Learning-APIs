package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageLogMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;

@ApplicationScoped
public class ConnectorRunRunMessagesBO extends BaseBo<ConnectorRunMessagesDTO, ConnectorRunMessagesEntity, ConnectorRunMessagesAO, ConnectorRunMessagesMapper> {

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    ConnectorRunAO runAo;

    @Inject
    ConnectorRunStepAO stepAO;

    @Inject
    ConnectorTransformerAO transformerAo;

    public List<ConnectorRunMessagesDTO> findByRunId(Long projectId) {
        return mapper.entitiesToDtos(ao.findByRun(projectId));
    }

    public void create(RunMessageLogDTO dto, Long stepId) {
        if (stepId != null) {
            RunMessageDTO toCreate = logMapper.logDtoToDto(dto);
            ConnectorRunMessagesDTO dataAnalysisDto = mapper.runDtoToDto(toCreate);
            ConnectorRunMessagesEntity toPersist = mapper.dtoToEntity(dataAnalysisDto);
            ConnectorRunStepEntity step = stepAO.findById(stepId);
            if (step != null) {
                toPersist.setStep(step);
                toPersist.setRun(step.getConnectorRun());
            } else if (dto.getRunId() != null) {
                toPersist.setRun(runAo.findById(dto.getRunId()));
            }
            ao.persist(toPersist);
        }
    }

    @Transactional
    public void createTransformerTransactional(String message, ConnectorRunMessageLevels severity, Long transformerId, Long runId) {
        ConnectorRunMessagesEntity toCreate = new ConnectorRunMessagesEntity();
        toCreate.setMessage(message);
        toCreate.setSeverity(severity);
        if (transformerId != null) {
            toCreate.setTransformer(transformerAo.findById(transformerId));
        }
        toCreate.setRun(runAo.findById(runId));
        toCreate.setType(RunMessageTypes.LOG);
        toCreate.setCreatedAt(new Date());
        persistAndClear(toCreate);
    }

    @Transactional
    public void createTransactional(String message, ConnectorRunMessageLevels severity, Long runId) {
        ConnectorRunMessagesEntity toCreate = new ConnectorRunMessagesEntity();
        toCreate.setMessage(message);
        toCreate.setType(RunMessageTypes.LOG);
        toCreate.setCreatedAt(new Date());
        toCreate.setSeverity(severity);
        toCreate.setRun(runAo.findById(runId));
        persistAndClear(toCreate);
    }

    private void persistAndClear(ConnectorRunMessagesEntity entity) {
        ao.persist(entity);
        ao.flush();
        ao.getEntityManager().clear();
    }
}
