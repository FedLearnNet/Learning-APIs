package de.unihamburg.daibetes.api.store.rating;

import bio.cosy.feddb.core.api.store.StoreRatingDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("store/ratings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Store", description = "Service for managing store Rating")
public interface StoreRatingRatingService {

    @POST
    @Path("app/{id}")
    @Operation(summary = "Creates a new App Ratings.")
    @APIResponse(responseCode = "200", description = "App Rating created or updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    StoreRatingDTO createForApp(@PathParam("id") Long id, @RequestBody @Valid StoreRatingCreateDTO createDTO);

    @POST
    @Path("model/{id}")
    @Operation(summary = "Creates a new App Ratings.")
    @APIResponse(responseCode = "200", description = "App Rating created or updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    StoreRatingDTO createForModel(@PathParam("id") Long id, @RequestBody @Valid StoreRatingCreateDTO createDTO);


    @GET
    @Path("app/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists all App Ratings.")
    @APIResponse(responseCode = "200", description = "List of App Ratings")
    @Transactional
    List<StoreRatingDTO> listForApp(@PathParam("id") Long id);

    @GET
    @Path("model/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists all App Ratings.")
    @APIResponse(responseCode = "200", description = "List of App Ratings")
    @Transactional
    List<StoreRatingDTO> listForModel(@PathParam("id") Long id);
}
