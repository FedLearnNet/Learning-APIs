package bio.cosy.feddb.local.api.importer.files;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Data
public class ConnectorFileUploadRequestDTO {
    private static final int MAX_FILES_PER_REQUEST = 20;

    @RestForm("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    @NotEmpty
    @Size(max = MAX_FILES_PER_REQUEST)
    private List<@NotNull FileUpload> files;

    @RestForm("settings")
    @PartType(MediaType.APPLICATION_JSON)
    @Valid
    private ConnectorFileUploadSettingsDTO settings;
}
