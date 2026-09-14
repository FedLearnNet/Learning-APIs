package rest.resource;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

public class RelayTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String DOCKER_IMAGE = "gitlab.cosy.bio:5050/cosybio/federated-learning/federated_db/feature-cloud-controller/controller-relay:staging";
    private GenericContainer<?> relay;

    @Override
    public Map<String, String> start() {
        relay = new GenericContainer<>(DOCKER_IMAGE)
                .withExposedPorts(9140, 9141);

        relay.start();
        String name = relay.getContainerName();
        return Map.of(
                "quarkus.datasource.jdbc.url", name
        );
    }

    @Override
    public void stop() {
        if (relay != null) {
            relay.stop();
        }
    }
}
