package bio.cosy.feddb.local.services.orch;

import bio.cosy.feddb.core.services.orch.clients.ComputingInfoServiceClient;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoDTO;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class ComputingInfoBO {

    @Inject
    @RestClient
    ComputingInfoServiceClient client;

    private volatile SystemComputingInfoDTO cached;

    public SystemComputingInfoDTO get() {
        return cached;
    }

    @PostConstruct
    public synchronized void refresh() {
        try {
            cached = client.getInfo();
        } catch (Exception e) {
            Log.warn("Could not refresh computing info", e);
        }
    }

}
