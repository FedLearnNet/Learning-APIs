package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyService;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;


@RegisterRestClient(configKey = "data-modeler-service")
@Produces(MediaType.APPLICATION_JSON)
public interface DataModelerOntologyService extends OntologyService {
}
