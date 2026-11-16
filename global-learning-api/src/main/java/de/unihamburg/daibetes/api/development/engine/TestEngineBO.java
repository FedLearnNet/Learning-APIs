package de.unihamburg.daibetes.api.development.engine;

import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.dto.ContainerRunDTO;
import bio.cosy.feddb.core.services.orch.dto.ContainerRunStatus;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class TestEngineBO {

    @Inject
    TestEngineRunBO runBO;

    @Inject
    @RestClient
    ContainerServiceClient containerClient;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    FLNetConfig config;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // An engine connects back with its app's id like a local engine
    public String provisionCredential(ExecutionAttemptEntity attempt) {
        if (!attempt.getStep().getRun().getEngineMode().connects()) {
            return null;
        }
        Long appId = attempt.getStep().getFederatedAppVersion().getFederatedApp().getId();
        return toolApiKeyService.issue(Scope.TEST_EMBED_APP, appId, attempt.getId());
    }

    public void customizeStart(ExecutionAttemptEntity attempt, StartWorkflowNodeDTO start) {
        Long appId = attempt.getStep().getFederatedAppVersion().getFederatedApp().getId();
        start.upsertEnv("APP_ID", String.valueOf(appId));
        if (!attempt.getStep().getRun().getEngineMode().connects()) {
            start.upsertEnv("TEST_MODE", "true");
            return;
        }
        FLNetConfig.FederatedTestConfig federated = config.testEngine().federated();
        if (federated.useDockerizedController()) {
            start.upsertEnv("FL_TEST__USE_DOCKERIZED_CONTROLLER", "true");
            start.upsertEnv("FL_TEST__DOCKERIZED_RELAY_URL", federated.relayUrl());
            start.upsertEnv("FL_TEST__DOCKERIZED_CONTROLLER_ORCH_URL", federated.controllerOrchUrl());
            start.upsertEnv("FL_TEST__DOCKERIZED_CONTROLLER_COMM_URL", federated.controllerCommUrl());
        }
    }

    public void watch(Long attemptId) {
        Uni.createFrom().item(() -> settle(attemptId, false))
                .runSubscriptionOn(virtualThreads)
                .repeat().withDelay(Duration.ofSeconds(2)).indefinitely()
                .select().where(Boolean::booleanValue)
                .select().first().toUni()
                .ifNoItem().after(config.testEngine().startTimeout())
                .recoverWithUni(() -> Uni.createFrom().item(() -> settle(attemptId, true)).runSubscriptionOn(virtualThreads))
                .subscribe().with(settled -> {
                }, e -> Log.warnf(e, "Failed to watch test engine attempt %d", attemptId));
    }

    public List<String> listLogs(Long appId, Long runId, String keycloakId) {
        return runBO.findContainerIdsTransactional(appId, runId, keycloakId).stream()
                .flatMap(containerId -> logsOf(containerId).stream()).toList();
    }

    private List<String> logsOf(String containerId) {
        try {
            ContainerRunDTO containerRun = containerClient.getRunOfContainer(containerId);
            if (containerRun == null || containerRun.getId() == null) {
                return List.of();
            }
            return containerClient.getRunLogs(containerRun.getId()).stream().map(log -> "[" + containerId + "] " + log.getLog()).toList();
        } catch (Exception e) {
            Log.warnf(e, "Failed to load logs of container %s", containerId);
            return List.of();
        }
    }

    private boolean settle(Long attemptId, boolean timedOut) {
        return runBO.settleTransactional(attemptId, exitCodeOf(runBO.findContainerIdTransactional(attemptId)), timedOut);
    }

    private Integer exitCodeOf(String containerId) {
        if (containerId == null) {
            return null;
        }
        try {
            ContainerRunDTO run = containerClient.getRunOfContainer(containerId);
            boolean ended = run.getStatus() == ContainerRunStatus.COMPLETED || run.getStatus() == ContainerRunStatus.FAILED;
            return ended ? run.getExitCode() : null;
        } catch (Exception e) {
            Log.debugf(e, "No container run for test engine container %s", containerId);
            return null;
        }
    }
}
