package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunAO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;

@ApplicationScoped
public class ConnectorRunRunMessagesBO extends BaseBo<ConnectorRunMessagesDTO, ConnectorRunMessagesEntity, ConnectorRunMessagesAO, ConnectorRunMessagesMapper> {

    @Inject
    ConnectorRunAO runAo;

    @Inject
    ConnectorTransformerAO transformerAo;

    public List<ConnectorRunMessagesDTO> findByRunId(Long runId) {
        return mapper.entitiesToDtos(ao.findByRun(runId));
    }

    @Transactional
    public void createTransformerTransactional(String message, ConnectorRunMessageLevels severity, Long transformerId, Long runId) {
        ConnectorRunMessagesEntity toCreate = newMessage(message, severity, runId);
        if (transformerId != null) {
            toCreate.setTransformer(transformerAo.findById(transformerId));
        }
        persistAndClear(toCreate);
    }

    @Transactional
    public void createTransactional(String message, ConnectorRunMessageLevels severity, Long runId) {
        persistAndClear(newMessage(message, severity, runId));
    }

    private ConnectorRunMessagesEntity newMessage(String message, ConnectorRunMessageLevels severity, Long runId) {
        ConnectorRunMessagesEntity toCreate = new ConnectorRunMessagesEntity();
        toCreate.setMessage(message);
        toCreate.setSeverity(severity);
        toCreate.setRun(runAo.findById(runId));
        toCreate.setCreatedAt(new Date());
        return toCreate;
    }

    private void persistAndClear(ConnectorRunMessagesEntity entity) {
        ao.persist(entity);
        ao.flush();
        ao.getEntityManager().clear();
    }
}
