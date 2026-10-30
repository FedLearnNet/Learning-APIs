package de.unihamburg.daibetes.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class OrchApiReadiness extends RemoteHealthCheck {

    @Inject
    @RestClient
    OrchHealthClient client;

    @Override
    protected String name() {
        return "orch-api";
    }

    @Override
    protected String probe() {
        return client.health();
    }
}
