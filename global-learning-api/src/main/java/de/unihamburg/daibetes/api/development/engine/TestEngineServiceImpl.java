package de.unihamburg.daibetes.api.development.engine;

import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;

@ApplicationScoped
public class TestEngineServiceImpl implements TestEngineService {

    public static final String TEST_ENGINE_CHANNEL = "test-engine-runs";

    @Inject
    TestEngineBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    @Channel(TEST_ENGINE_CHANNEL)
    Multi<ExecutionRunDetailDTO> engineChanges;

    @Override
    public List<ExecutionRunDetailDTO> list(Long appId) {
        return bo.listTransactional(appId, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO start(Long appId, TestEngineCreateDTO create) {
        return bo.startTransactional(appId, create, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO stop(Long appId, Long id) {
        return bo.stopTransactional(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public Multi<ExecutionRunDetailDTO> stream(Long appId) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom().voidItem()
                .invoke(() -> bo.requireAuthorTransactional(appId, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(ignored -> engineChanges.filter(run -> appId.equals(run.getFederatedAppId())));
    }

    @Override
    public List<String> logs(Long appId, Long id) {
        return bo.listLogs(appId, id, userIdentity.getKeycloakId());
    }
}
