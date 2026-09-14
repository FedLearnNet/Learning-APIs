package bio.cosy.feddb.local.services.datamodler;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyService;
import bio.cosy.feddb.local.services.GlobalAPIAuthRequestFilter;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;


@RegisterRestClient(configKey = "global-schema-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalOntologyService extends OntologyService {
}
