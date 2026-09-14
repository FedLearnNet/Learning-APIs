package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepMessageSender;
import bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@ApplicationScoped
public class AppTransformerPreviewBO {

    @Inject
    ConnectorPreviewBO previewBO;

    @Inject
    AppTransformerSessionBO sessionBO;

    @Inject
    ConnectorRunStepMessageSender messageSender;

    @Inject
    AppTransformerPreviewMapper mapper;

    @Inject
    Instance<AppTransformerPreviewBO> self;

    @ActivateRequestContext
    public Multi<AppTransformerPreviewStreamDTO> start(AppTransformerPreviewRequestDTO request, String keycloakId) {
        if (request == null || request.getConfig() == null) {
            throw new BadRequestException("A connector configuration is required to run a transformation step");
        }
        if (request.getStepIndex() == null) {
            throw new BadRequestException("A transformation step index is required");
        }
        int stepIndex = request.getStepIndex();
        ConnectorConfigDTO config = request.getConfig();
        ConnectorPreviewAppStageInput input = previewBO.prepareAppStage(config, stepIndex);

        return Multi.createFrom().emitter(emitter ->
                Infrastructure.getDefaultExecutor().execute(
                        () -> self.get().run(config, stepIndex, input, keycloakId, emitter)));
    }

    @ActivateRequestContext
    public void run(ConnectorConfigDTO config,
                    int stepIndex,
                    ConnectorPreviewAppStageInput input,
                    String keycloakId,
                    MultiEmitter<? super AppTransformerPreviewStreamDTO> emitter) {
        AtomicReference<ConnectorRunStepDTO> step = new AtomicReference<>();
        Long sessionId = null;
        try {
            sessionId = sessionBO.openPreview(input.getTransformer(), keycloakId, created -> {
                step.set(created);
                emitter.emit(message(created, stepIndex, false, null));
            });

            List<Map<String, Object>> rows = sessionBO.transform(sessionId, input.getTransformer(), input.getRows());
            previewBO.storeAppStage(config.getConnectorId(), stepIndex, input.getTransformer(),
                    input.getFingerprint(), rows, columnsOf(input.getColumns(), rows));

            AppTransformerPreviewStreamDTO done = message(step.get(), stepIndex, true, null);
            done.setStatus(RunStatusTypes.FINISHED);
            done.setProgress(1f);
            done.setRowCount(rows.size());
            publish(emitter, done);
            Log.infof("App transformer '%s' produced %d preview row(s) for step %d",
                    input.getTransformer().getAppImage(), rows.size(), stepIndex);
        } catch (Exception e) {
            Log.errorf("App transformer preview of step %d failed: %s", stepIndex, e.getMessage());
            publish(emitter, message(step.get(), stepIndex, true, e.getMessage()));
        } finally {
            if (sessionId != null) {
                sessionBO.close(sessionId);
            }
            emitter.complete();
        }
    }


    private void publish(MultiEmitter<? super AppTransformerPreviewStreamDTO> emitter,
                         AppTransformerPreviewStreamDTO message) {
        messageSender.sendMessage(message);
        emitter.emit(message);
    }

    private AppTransformerPreviewStreamDTO message(ConnectorRunStepDTO step,
                                                   int stepIndex,
                                                   boolean finished,
                                                   String error) {
        AppTransformerPreviewStreamDTO message = step == null
                ? new AppTransformerPreviewStreamDTO()
                : mapper.dtoToStreamDTO(step);
        message.setStepIndex(stepIndex);
        message.setFinished(finished);
        if (error != null) {
            message.setStatus(RunStatusTypes.ERROR);
            message.setLastError(error);
        }
        return message;
    }

    private static List<String> columnsOf(List<String> columns, List<Map<String, Object>> rows) {
        LinkedHashSet<String> all = new LinkedHashSet<>(columns == null ? List.of() : columns);
        rows.forEach(row -> all.addAll(row.keySet()));
        return new ArrayList<>(all);
    }
}
