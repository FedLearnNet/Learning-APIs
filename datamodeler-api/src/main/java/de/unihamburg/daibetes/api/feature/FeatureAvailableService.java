package de.unihamburg.daibetes.api.feature;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/feature-available")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Feature Available", description = "Service for checking which features are available.")
public interface FeatureAvailableService {

    @GET
    @Operation(
            summary = "Which features are available",
            description = "JSON which boolean flags are set to true for available features."
    )
    FeatureAvailableDTO get();
}
