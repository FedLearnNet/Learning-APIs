package bio.cosy.feddb.local.health;

import bio.cosy.feddb.local.services.controller.LocalControllerHealthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class ControllerReadiness implements HealthCheck {
    @Inject
    @RestClient
    LocalControllerHealthService client;

    @Override
    public HealthCheckResponse call() {
        long start = System.nanoTime();
        try {
            String json = client.health(); // 200 = UP
            long ms = (System.nanoTime() - start) / 1_000_000;
            return HealthCheckResponse.named("controller")
                    .status(true)
                    .withData("latencyMs", ms)
                    .withData("payload", json)
                    .build();
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return HealthCheckResponse.named("controller")
                    .status(false)
                    .withData("latencyMs", ms)
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
