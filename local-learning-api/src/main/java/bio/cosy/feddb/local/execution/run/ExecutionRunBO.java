package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptWebsocketService;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class ExecutionRunBO {

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunMapper mapper;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    OpenConnections connections;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    @Inject
    @Channel(ExecutionRunSender.RUN_CHANNEL)
    Multi<ExecutionRunDTO> changes;

    @Transactional
    public ExecutionRunDTO findRunTransactional(Long runId, boolean withMessages) {
        return runAO.findByIdOptional(runId)
                .map(run -> mapper.mapRunEntityToDTO(run, withMessages))
                .orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
    }

    public Multi<ExecutionRunDTO> subscribe(Long runId) {
        Multi<ExecutionRunDTO> live = changes.filter(run -> runId.equals(run.getId()));
        Multi<ExecutionRunDTO> current = Multi.createFrom().item(() -> findRunTransactional(runId, false))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        Multi<ExecutionRunDTO> merged = Multi.createBy().merging().streams(live, current);
        return Multi.createFrom().emitter(emitter -> {
            Cancellable subscription = merged.subscribe().with(run -> {
                emitter.emit(run);
                if (run.hasEnded()) {
                    emitter.complete();
                }
            }, emitter::fail, emitter::complete);
            emitter.onTermination(subscription::cancel);
        });
    }

    public Uni<ExecutionRunDTO> awaitEnd(Long runId, Duration timeout) {
        return subscribe(runId).collect().last()
                .ifNoItem().after(timeout).recoverWithUni(() -> Uni.createFrom().<ExecutionRunDTO>item(() -> {
                            workflowExecutionBO.stopTransactional(runId);
                            return null;
                        })
                        .runSubscriptionOn(virtualThreads)
                        .onItem().failWith(() -> new IllegalStateException("Run " + runId + " did not finish within " + timeout)));
    }

    public boolean sendToApp(Long attemptId, AppMessageWrapperDTO<?> message) {
        Optional<WebSocketConnection> connection = connectionOf(attemptId);
        if (connection.isEmpty()) {
            Log.warnf("App of attempt %d is not connected, %s not sent", attemptId, message.getType());
            return false;
        }
        connection.get().sendTextAndAwait(message);
        return true;
    }

    @Transactional
    public Optional<Long> findLatestAttemptIdTransactional(Long runId) {
        return attemptAO.findLatestIdOfRunOptional(runId);
    }

    public Optional<WebSocketConnection> connectionOf(Long attemptId) {
        return connections.findByEndpointId(ExecutionAttemptWebsocketService.class.getName()).stream()
                .filter(connection -> !connection.isClosed())
                .filter(connection -> Objects.equals(String.valueOf(attemptId), connection.pathParam("runId")))
                .findFirst();
    }
}
