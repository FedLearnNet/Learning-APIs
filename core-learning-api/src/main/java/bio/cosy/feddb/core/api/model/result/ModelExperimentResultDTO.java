package bio.cosy.feddb.core.api.model.result;

import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Data
public class ModelExperimentResultDTO {
    @RestForm("files")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private List<FileUpload> files;

    @RestForm("globalFLExperimentUniqueId")
    @PartType(MediaType.TEXT_PLAIN)
    private String globalFLExperimentUniqueId;

    @RestForm("appVersionId")
    @PartType(MediaType.TEXT_PLAIN)
    private Long appVersionId;

    @RestForm("clinicId")
    @PartType(MediaType.TEXT_PLAIN)
    private String clinicId;
}
