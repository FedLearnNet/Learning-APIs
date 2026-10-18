package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyService;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.oidc.client.filter.OidcClientFilter;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;


// service account token, also used without a user (async query handling)
@RegisterRestClient(configKey = "data-modeler-service")
@OidcClientFilter
@Produces(MediaType.APPLICATION_JSON)
public interface DataModelerOntologyService extends OntologyService {
}
