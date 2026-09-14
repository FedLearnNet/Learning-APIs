package de.unihamburg.daibetes.api.observer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

@ApplicationScoped
public class FLNetClientObserverEmitter {

    public static final String CLIENT_OBSERVER_CHANNEL = "client-observer-events";

    @Inject
    @ConfigProperty(name = "flnet.observer.enabled", defaultValue = "false")
    boolean observerEnabled;

    @Inject
    @Channel(CLIENT_OBSERVER_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<FLNetClientObserverEventDTO> observerEmitter;

    public void emit(FLNetClientObserverEventDTO event) {
        if (!observerEnabled) {
            return;
        }
        try {
            observerEmitter.send(event);
        } catch (Exception e) {
            Log.debug("Observer SSE client already closed, dropping event.", e);
        }
    }
}
