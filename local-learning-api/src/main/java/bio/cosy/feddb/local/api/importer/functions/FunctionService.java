package bio.cosy.feddb.local.api.importer.functions;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/connector/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Functions", description = "Service for managed mapping and transformation functions")
@Authenticated
public interface FunctionService {

    @GET
    @Operation(summary = "List all functions grouped by module")
    @APIResponse(responseCode = "200", description = "All functions")
    FunctionsDTO list();

    @GET
    @Path("/detail")
    @Operation(summary = "Get all functions in detail")
    @APIResponse(responseCode = "200", description = "Detailed function list")
    List<FunctionsDetailDTO> getAllDetail();

    @GET
    @Path("/{methodName}")
    @Operation(summary = "Get a single function")
    @APIResponse(responseCode = "200", description = "Function found")
    @APIResponse(responseCode = "404", description = "Function not found")
    FunctionsDetailDTO retrieve(
            @PathParam("methodName") String methodName,
            @Parameter(in = ParameterIn.QUERY, description = "Name of the module, default builtin")
            @QueryParam("module_name")
            @DefaultValue("builtin")
            String moduleName
    );
}
