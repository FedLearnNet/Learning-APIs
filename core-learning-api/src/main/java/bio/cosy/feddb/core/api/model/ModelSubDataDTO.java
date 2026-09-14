package bio.cosy.feddb.core.api.model;

import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Data
public class ModelSubDataDTO {
    @RestForm("runId")
    @PartType(MediaType.TEXT_PLAIN)
    private Long runId;

    @RestForm("name")
    @PartType(MediaType.TEXT_PLAIN)
    private String name;

    @RestForm("filePath")
    @PartType(MediaType.TEXT_PLAIN)
    private String filePath;

    @RestForm("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private FileUpload file;
}
