package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.ConfigYMLDTO;
import bio.cosy.feddb.core.services.orch.dto.InspectVolumeResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.VolumeUploadForm;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/volume")
@Produces("application/json")
@Consumes("application/json")
public interface VolumeServiceClient {

    @GET
    List<InspectVolumeResponseDTO> listVolumes();

    @GET
    @Path("{name}")
    InspectVolumeResponseDTO getVolume(@PathParam("name") String name);

    @POST
    @Path("{name}")
    Response createVolume(@PathParam("name") String name);


    @POST
    @Path("{name}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadFiles(@PathParam("name") String name, @BeanParam VolumeUploadForm form);

    @POST
    @Path("workflow/{workflowId}/node/{workflowNodeId}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadFilesIds(@PathParam("workflowId") Long workflowId,
                            @PathParam("workflowNodeId") Long workflowNodeId,
                            @BeanParam VolumeUploadForm form);

    @POST
    @Path("workflow/{workflowId}/node/{workflowNodeId}/upload/config")
    Response uploadFilesIdsConfig(@PathParam("workflowId") Long workflowId,
                                  @PathParam("workflowNodeId") Long workflowNodeId,
                                  @Valid ConfigYMLDTO config);

    @POST
    @Path("{name}/config/upload")
    Response uploadConfig(@PathParam("name") String name, @Valid ConfigYMLDTO config);

    @GET
    @Path("{name}/download")
    @Produces("application/zip")
    Response downloadFiles(@PathParam("name") String name);

    @GET
    @Path("workflow/{workflowId}/node/{workflowNodeId}/download")
    @Produces("application/zip")
    Response downloadFilesIds(@PathParam("workflowId") Long workflowId,
                              @PathParam("workflowNodeId") Long workflowNodeId);

    @PUT
    @Path("workflow/{workflowId}/node/{workflowNodeId}/move-to-output")
    Response moveFilesToOutput(@PathParam("workflowId") Long workflowId,
                               @PathParam("workflowNodeId") Long workflowNodeId);

    @GET
    @Path("{name}/size")
    Response volumeSize(@PathParam("name") String name);

    @DELETE
    @Path("{name}")
    Response removeVolume(@PathParam("name") String name);

    @DELETE
    Response removeAllVolumes();
}
