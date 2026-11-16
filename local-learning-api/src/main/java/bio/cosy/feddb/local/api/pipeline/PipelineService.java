package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineCallbackService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline", description = "Callbacks of the local Tool-Build-Pipeline, authorized by the pipeline secret")
public interface PipelineService extends PipelineCallbackService {
}
