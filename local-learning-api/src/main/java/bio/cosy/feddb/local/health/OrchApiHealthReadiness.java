package bio.cosy.feddb.local.health;

import bio.cosy.feddb.local.services.orch.OrchHealthServiceClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class OrchApiHealthReadiness implements HealthCheck {

    @Inject
    @RestClient
    OrchHealthServiceClient client;

    @Override
    public HealthCheckResponse call() {
        long start = System.nanoTime();
        try {
            String json = client.ready(); // 200 = UP
            long ms = (System.nanoTime() - start) / 1_000_000;
            return HealthCheckResponse.named("orch-api-remote")
                    .status(true)
                    .withData("latencyMs", ms)
                    .withData("payload", json) // optional: Rohdaten
                    .build();
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return HealthCheckResponse.named("orch-api-remote")
                    .status(false)
                    .withData("latencyMs", ms)
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
