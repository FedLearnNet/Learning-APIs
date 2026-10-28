package de.unihamburg.daibetes.api.info;

import bio.cosy.feddb.core.api.info.InfoDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/info")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Info", description = "Information about this service, e.g. its version")
public interface InfoService {

    @GET
    @PermitAll
    @Operation(summary = "Data modeler information",
            description = "Public. Version and state of the startup imports (ontologies, data types, schemas)")
    @APIResponse(responseCode = "200", description = "Data modeler information")
    InfoDTO getInfo();
}
