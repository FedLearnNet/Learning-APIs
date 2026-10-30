package de.unihamburg.daibetes.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

public abstract class RemoteHealthCheck implements HealthCheck {

    protected abstract String name();
    protected abstract String probe();

    @Override
    public HealthCheckResponse call() {
        long start = System.nanoTime();
        try {
            String payload = probe();
            return HealthCheckResponse.named(name())
                    .status(true)
                    .withData("latencyMs", elapsedMs(start))
                    .withData("payload", payload == null ? "" : payload)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named(name())
                    .status(false)
                    .withData("latencyMs", elapsedMs(start))
                    .withData("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
