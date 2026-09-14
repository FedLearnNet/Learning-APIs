package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "orch-docker-service")
public interface OrchWorkflowServiceClient extends WorkflowServiceClient {

}
