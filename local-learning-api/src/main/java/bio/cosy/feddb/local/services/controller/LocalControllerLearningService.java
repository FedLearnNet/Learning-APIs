package bio.cosy.feddb.local.services.controller;


import bio.cosy.feddb.core.services.controller.ControllerLearningService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "controller-api")
public interface LocalControllerLearningService extends ControllerLearningService {

}
