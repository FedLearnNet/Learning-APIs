package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.Objects;

import static bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepMessageSender.CONNECTOR_APP_BASED_EXECUTION_CHANNEL;

@ApplicationScoped
public class AppTransformerPreviewServiceImpl implements AppTransformerPreviewService {

    @Inject
    @Channel(CONNECTOR_APP_BASED_EXECUTION_CHANNEL)
    Multi<ConnectorRunStepDTO> runProcessInfo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    AppTransformerPreviewBO appTransformerPreviewBO;

    @Inject
    AppTransformerPreviewMapper mapper;

    @Override
    public Multi<AppTransformerPreviewStreamDTO> previewStep(AppTransformerPreviewRequestDTO request) {
        String keycloakId = userIdentity.getKeycloakId();

        return Multi.createFrom().deferred(() -> appTransformerPreviewBO.start(request, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToMultiAndConcatenate(dto -> {
                    if (dto == null || dto.getId() == null) {
                        return Multi.createFrom().item(dto);
                    }
                    Long stepId = dto.getId();
                    return Multi.createBy().concatenating().streams(
                            Multi.createFrom().item(dto),
                            runProcessInfo
                                    .filter(step -> step != null && Objects.equals(step.getId(), stepId))
                                    .map(this::asStreamDTO));
                });
    }

    private AppTransformerPreviewStreamDTO asStreamDTO(ConnectorRunStepDTO step) {
        return step instanceof AppTransformerPreviewStreamDTO stream ? stream : mapper.dtoToStreamDTO(step);
    }
}
