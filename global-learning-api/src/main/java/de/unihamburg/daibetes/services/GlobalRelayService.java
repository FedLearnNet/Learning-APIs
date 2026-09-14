package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.services.controller.RelayService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "relay-api")
public interface GlobalRelayService extends RelayService {
}
