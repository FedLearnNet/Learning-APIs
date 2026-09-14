package bio.cosy.feddb.local.api.datamodeler;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.base.PagedResponse;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path(DataModelForwardService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Data Model Forward", description = "Forwards selected data modeler queries through the local API")
public interface DataModelForwardService {
    String PATH = "/datamodeler";

    @GET
    @Path("/ontology")
    @Operation(summary = "Search ontologies", description = "Autocomplete/search ontologies from the configured global data modeler.")
    PagedResponse<OntologyNodeDTO> searchOntologies(
            @QueryParam("search") String search,
            @DefaultValue("0") @QueryParam("page") int page,
            @DefaultValue("20") @QueryParam("page_size") int pageSize
    );

    @GET
    @Path("/ontology/{ontologyId}/datatype")
    @Operation(summary = "List datatypes for ontology", description = "Lists datatypes available for a selected ontology.")
    PagedResponse<DataTypeNodeDTO> getDatatypesForOntology(
            @PathParam("ontologyId") UUID ontologyId,
            @QueryParam("search") String search,
            @DefaultValue("0") @QueryParam("page") int page,
            @DefaultValue("100") @QueryParam("page_size") int pageSize
    );
}
