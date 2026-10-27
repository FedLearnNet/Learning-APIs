package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;



@ApplicationScoped
public class AppTransformerPreviewServiceImpl implements AppTransformerPreviewService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    AppTransformerPreviewBO appTransformerPreviewBO;

    @Override
    public Multi<AppTransformerPreviewStreamDTO> previewStep(AppTransformerPreviewRequestDTO request) {
        String keycloakId = userIdentity.getKeycloakId();
        return Multi.createFrom().deferred(() -> appTransformerPreviewBO.start(request, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}
