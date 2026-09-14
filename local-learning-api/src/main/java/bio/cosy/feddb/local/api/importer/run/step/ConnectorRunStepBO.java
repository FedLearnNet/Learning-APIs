package bio.cosy.feddb.local.api.importer.run.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunMapper;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunResultSender;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionRunBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class ConnectorRunStepBO extends BaseBo<ConnectorRunStepDTO, ConnectorRunStepEntity, ConnectorRunStepAO, ConnectorRunStepMapper> {

    @Inject
    ConnectorRunMapper runMapper;

    @Inject
    ConnectorRunResultSender resultSender;

    @Inject
    ConnectorRunStepMessageSender messageSender;

    @Inject
    ConnectorRunExecutionRunBO executionRunBO;

    @Override
    public ConnectorRunStepEntity mergeEntity(ConnectorRunStepEntity foundEntity, ConnectorRunStepEntity entity) {
        foundEntity.setStatus(entity.getStatus());
        foundEntity.setLastError(entity.getLastError());
        foundEntity.setProgress(entity.getProgress());
        foundEntity.setContainerId(entity.getContainerId());

        if (entity.getConnectorRun() != null && entity.getConnectorRun().getId() != null) {
            foundEntity.setConnectorRun(
                    ao.getEntityManager().getReference(ConnectorRunEntity.class, entity.getConnectorRun().getId())
            );
        }

        if (entity.getTransformation() != null && entity.getTransformation().getId() != null) {
            foundEntity.setTransformation(
                    ao.getEntityManager().getReference(ConnectorTransformerEntity.class, entity.getTransformation().getId())
            );
        }

        return foundEntity;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ConnectorRunStepDTO createInNewTransaction(ConnectorRunStepDTO request) {
        ConnectorRunStepEntity entity = mapper.dtoToEntity(request);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public ConnectorRunStepDTO finishEntityById(Long id) {
        ao.updateStatusTransactional(id, RunStatusTypes.FINISHED, 100f, null);
        ConnectorRunStepEntity entity = ao.findById(id);
        if (entity != null) {
            ConnectorRunStepDTO runDTO = mapper.entityToDto(entity);
            messageSender.sendMessage(runDTO);

            ConnectorRunEntity runEntity = entity.getConnectorRun();
            if (runEntity != null) {
                resultSender.sendUpdate(runMapper.entityToDto(runEntity));
            }
            return runDTO;
        }
        return null;
    }

    /**
     * Reads a step's current status in its own transaction.
     *
     * <p>Deliberately a new transaction: the caller polling this runs on the import thread, which
     * holds a request-scoped session for the length of the run, while the status it is watching for
     * is written by the websocket thread. Reading it in the caller's session would keep handing
     * back the entity as it looked the first time it was loaded.</p>
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public RunStatusTypes statusOf(Long id) {
        ConnectorRunStepEntity entity = ao.findById(id);
        return entity == null ? null : entity.getStatus();
    }

    /**
     * Fails a step whose app connection dropped before it reported a terminal status.
     *
     * <p>Request-scoped for the same reason as {@link #updateAndNotify}: the websocket close and
     * error callbacks run on a vert.x worker thread with no transaction and no CDI request context,
     * so reading the step there threw before it could decide anything. It lives here rather than on
     * the websocket endpoint because that endpoint calls it from its own {@code @OnClose}, and a
     * self-invocation would bypass the interceptor that activates the context.</p>
     *
     * <p>A step that already finished is left alone - a connection closing after the app uploaded
     * its output is the normal end of a run, not a failure.</p>
     */
    @ActivateRequestContext
    public void failIfUnfinished(Long id, String logMessage) {
        ConnectorRunStepEntity entity = ao.findById(id);
        if (entity == null) {
            Log.debugf("No connector run step %d to close", id);
            return;
        }
        if (RunStatusTypes.isFinalStatus(entity.getStatus())) {
            Log.debugf("ConnectorRunStep %d already ended as %s, nothing to close", id, entity.getStatus());
            return;
        }
        updateAndNotify(id, RunStatusTypes.ERROR, logMessage);
    }

    /**
     * Applies a status change and notifies the run's listeners.
     *
     * <p>Request-scoped on purpose: the app reports its status over the websocket, which Quarkus
     * dispatches on a worker thread that has neither a transaction nor a CDI request context. The
     * status write below carries its own transaction, but reading the step back - and walking its
     * lazy {@code connectorRun} to notify - needs a session, and without one every status message
     * the app sent died with a ContextNotActiveException.</p>
     */
    @ActivateRequestContext
    public void updateAndNotify(Long id, RunStatusTypes status, String logMessage) {

        if (status == RunStatusTypes.FINISHED || status == RunStatusTypes.ERROR) {
            ao.updateStatusTransactional(id, status, 100f, logMessage);

        } else {
            ao.updateStatusTransactional(id, status);
        }
        ConnectorRunStepEntity entity = ao.findById(id);
        messageSender.sendMessage(mapper.entityToDto(entity));

        Log.infof("ConnectorRunStep %d updated to status=%s", id, status);
        if (status == RunStatusTypes.FINISHED || status == RunStatusTypes.ERROR) {
            // A step that never got a container - the image could not be pulled, orch-api refused
            // the start - has nothing to clean up, and asking orch-api to remove container "null"
            // threw over the top of the real error, so the run reported "Param was null" instead
            // of "Model image not found".
            String containerId = entity.getContainerId();
            if (containerId == null || containerId.isBlank()) {
                Log.debugf("ConnectorRunStep %d has no container to clean", id);
            } else {
                executionRunBO.cleanup(containerId);
                Log.infof("ConnectorRunStep %d cleaned", id);
            }
        }

        // Push run-level update through the channel so SSE clients get notified
        ConnectorRunEntity runEntity = entity.getConnectorRun();
        if (runEntity != null) {
            ConnectorRunDTO runDTO = runMapper.entityToDto(runEntity);
            resultSender.sendUpdate(runDTO);
        }
    }
}
