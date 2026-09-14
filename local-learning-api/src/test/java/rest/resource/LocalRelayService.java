package rest.resource;

import bio.cosy.feddb.core.services.controller.RelayService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "test-relay-api")
public interface LocalRelayService extends RelayService {
}
