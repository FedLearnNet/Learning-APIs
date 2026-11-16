package bio.cosy.feddb.local.api.importer.connector.assistant;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConnectorAssistantServiceImpl implements ConnectorAssistantService {

    @Inject
    ConnectorAssistantBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    public ConnectorAssistantRunDTO start(ConnectorDTO connector) {
        return bo.start(connector, userIdentity.getKeycloakId());
    }

    @Override
    public Multi<TransformerAssistantMessageDTO> events(String runId) {
        return bo.events(runId, userIdentity.getKeycloakId());
    }

    @Override
    public void message(String runId, TransformerAssistantMessageDTO message) {
        bo.message(runId, message, userIdentity.getKeycloakId());
    }
}
