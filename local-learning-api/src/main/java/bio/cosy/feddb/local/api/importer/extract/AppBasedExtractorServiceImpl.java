package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;



@ApplicationScoped
public class AppBasedExtractorServiceImpl implements AppBasedExtractorService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    AppBasedExtractorBO appBasedExtractorBO;

    // The extractor's run as it goes, or a finished one from the cache.
    @Override
    public Multi<ConnectorExtractorStreamDTO> post(AppBasedExtractorRequestDTO request) {
        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }
        String keycloakId = userIdentity.getKeycloakId();
        return Multi.createFrom().deferred(() -> appBasedExtractorBO.start(request, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}
