package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunMapper;
import bio.cosy.feddb.local.execution.run.WorkflowExecutionBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;

@ApplicationScoped
public class ConnectorAppRunSetupBO {

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionRunMapper mapper;

    @Inject
    ConnectorRunAO connectorRunAO;

    @Inject
    ConnectorTransformerAO transformerAO;

    @Inject
    CohortAO cohortAO;

    public ExecutionRunDTO start(ConnectorTransformerDTO app, LinkedHashMap<String, Object> hyperParams, Long connectorRunId,
                                 Long cohortId, String keycloakId) {
        String name = app.getMethodName() != null ? app.getMethodName() : app.getAppImage();
        ExecutionRunEntity run = workflowExecutionBO.executeAppTransactional(name, hyperParams, step -> {
            step.setAppImage(app.getAppImage());
            step.setGlobalAppVersionId(app.getAppVersionId());
            if (app.getId() != null) {
                step.setTransformer(transformerAO.findById(app.getId()));
            }
        });
        run.setKeycloakId(keycloakId);
        run.setConnectorRun(connectorRunId != null ? connectorRunAO.findById(connectorRunId) : null);
        run.setCohort(cohortId != null ? cohortAO.findById(cohortId) : null);
        Log.infof("Started app %s as run %d", app.getAppImage(), run.getId());
        return mapper.mapRunEntityToDTO(run, false);
    }
}
