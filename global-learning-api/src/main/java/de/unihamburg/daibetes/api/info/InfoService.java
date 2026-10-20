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
@Tag(name = "Info", description = "Information about this Platform, e.g. its version")
public interface InfoService {

    @GET
    @PermitAll
    @Operation(summary = "Platform information", description = "Public. Also used by the Clients to detect a newer Platform version")
    @APIResponse(responseCode = "200", description = "Platform information")
    InfoDTO getInfo();
}
