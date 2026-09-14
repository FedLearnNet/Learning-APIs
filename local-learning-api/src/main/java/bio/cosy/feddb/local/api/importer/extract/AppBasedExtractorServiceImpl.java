package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.Objects;

import static bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepMessageSender.CONNECTOR_APP_BASED_EXECUTION_CHANNEL;

@ApplicationScoped
public class AppBasedExtractorServiceImpl implements AppBasedExtractorService {

    @Inject
    @Channel(CONNECTOR_APP_BASED_EXECUTION_CHANNEL)
    Multi<ConnectorRunStepDTO> runProcessInfo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    AppBasedExtractorBO appBasedExtractorBO;

    @Inject
    AppExtractorMapper mapper;

    @Override
    public Multi<ConnectorExtractorStreamDTO> post(AppBasedExtractorRequestDTO request) {
        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        String keycloakId = userIdentity.getKeycloakId();

        return Multi.createFrom().deferred(() -> {
                    Multi<ConnectorExtractorStreamDTO> startedStream = appBasedExtractorBO.start(request, keycloakId);

                    if (startedStream == null) {
                        throw new BadRequestException("Could not start app-based extractor");
                    }

                    return startedStream;
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToMultiAndConcatenate(dto -> {
                    if (dto == null) {
                        return Multi.createFrom().empty();
                    }

                    if (Boolean.TRUE.equals(dto.getCached())) {
                        return Multi.createFrom().item(dto);
                    }

                    if (dto.getId() == null) {
                        return Multi.createFrom().item(dto);
                    }

                    Long stepId = dto.getId();

                    return Multi.createBy().concatenating().streams(
                            Multi.createFrom().item(dto),
                            runProcessInfo.filter(step ->
                                    step != null &&
                                            step.getId() != null &&
                                            Objects.equals(step.getId(), stepId)
                            ).map(mapper::dtoToStreamDTO)
                    );
                });
    }
}
