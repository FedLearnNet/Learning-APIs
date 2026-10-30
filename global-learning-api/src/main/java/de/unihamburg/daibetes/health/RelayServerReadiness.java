package de.unihamburg.daibetes.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class RelayServerReadiness extends RemoteHealthCheck {

    @Inject
    @RestClient
    RelayHealthClient client;

    @Override
    protected String name() {
        return "relay-server";
    }

    @Override
    protected String probe() {
        return client.health();
    }
}
