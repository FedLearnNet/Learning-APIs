package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.services.controller.ControllerLearningService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "local-controller")
public interface LocalControllerLearningService extends ControllerLearningService {
}
