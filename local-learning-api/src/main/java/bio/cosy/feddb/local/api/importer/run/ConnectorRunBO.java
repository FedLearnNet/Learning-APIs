package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class ConnectorRunBO extends BaseBo<ConnectorRunDTO, ConnectorRunEntity, ConnectorRunAO, ConnectorRunMapper> {

    @Inject
    Instance<ConnectorRunBO> self;

    @Inject
    ConnectorAO connectorAO;

    @Inject
    CohortAO cohortAO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunETLBO etl;


    public List<ConnectorRunDTO> getAllForConnector(Long connectorId) {
        ConnectorEntity connector = connectorAO.findById(connectorId);
        if (connector == null) {
            throw new NotFoundException("Connector not found");
        }
        return mapper.entitiesToDtos(ao.getAllForConnector(connectorId));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ConnectorRunDTO createRun(ConnectorDTO connectorDTO, boolean deleteExistingPatients, boolean dryRun, String keycloakId) {
        ConnectorRunEntity entity = new ConnectorRunEntity();
        entity.setConnector(connectorAO.findById(connectorDTO.getId()));
        entity.setCohort(cohortAO.findById(connectorDTO.getCohortId()));
        entity.setProgress(1L);
        entity.setStatus(ImportStatusEnum.INIT);
        entity.setCurrentStep(ConnectorRunStep.INIT);
        entity.setKeycloakId(keycloakId);
        entity.setDeleteExistingPatients(deleteExistingPatients);
        entity.setDryRun(dryRun);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public ConnectorRunDTO startRun(Long connectorId, boolean deleteExistingPatients, boolean dry, String keycloakId) {
        ConnectorDTO connector = connectorBO.getById(connectorId);
        if (connector == null) {
            throw new NotFoundException(
                    String.format("Connector with id %d was not found", connectorId));
        }

        ConnectorRunDTO run = self.get().createRun(connector, deleteExistingPatients, dry, keycloakId);

        etl.processRunAsync(run, connector);
        return run;
    }
}
