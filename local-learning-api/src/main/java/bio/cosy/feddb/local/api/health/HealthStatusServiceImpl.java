package bio.cosy.feddb.local.api.health;

import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class HealthStatusServiceImpl implements HealthStatusService {

    @Inject
    SmallRyeHealthReporter reporter;

    @Override
    public Response ready() {
        SmallRyeHealth health = reporter.getReadiness();
        return Response.status(health.isDown() ? Response.Status.SERVICE_UNAVAILABLE : Response.Status.OK)
                .entity(health.getPayload().toString())
                .build();
    }
}
