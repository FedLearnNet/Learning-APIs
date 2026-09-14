package bio.cosy.feddb.core.services.orch.dto;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jboss.resteasy.reactive.PartType;

import java.io.File;

/**
 * Data Transfer Object (DTO) representing a volume upload form.
 * This class is used to handle file uploads in a Orch API context.
 * It contains a single field for the file to be uploaded.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VolumeUploadForm {

    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public File file;

    @FormParam("fileName")
    @PartType(MediaType.TEXT_PLAIN)
    public String fileName;

}
