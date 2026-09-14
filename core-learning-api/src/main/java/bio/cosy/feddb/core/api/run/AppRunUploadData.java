package bio.cosy.feddb.core.api.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Data
public class AppRunUploadData {
    public static final String FILE_CUSTOM_HEADER_KEY = "X-Key";
    public static final String FILE_CUSTOM_HEADER_NAME = "X-Name";

    @RestForm("runId")
    @PartType(MediaType.TEXT_PLAIN)
    Long runId;

    @RestForm("fields")
    @PartType(MediaType.APPLICATION_JSON)
    private LinkedHashMap<String, Object> fields;

    // Wrapper-measured run metadata (timings). Carried on the upload because for data-analysis
    // runs the upload is the terminal call (it marks the run FINISHED and tears the container
    // down), so the websocket FINISH_RUN never arrives.
    @RestForm("meta")
    @PartType(MediaType.APPLICATION_JSON)
    private RunMetaDTO meta;

    @RestForm("files")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private List<FileUpload> files;

    @JsonIgnore
    public LinkedHashMap<String, Object> getFieldsNullsafe() {
        return Optional.ofNullable(fields).orElseGet(LinkedHashMap::new);
    }

    @JsonIgnore
    public List<FileUpload> getFilesNullsafe() {
        return Optional.ofNullable(files).orElseGet(List::of);
    }

    public static String getKey(FileUpload file) {
        return file.getHeaders().getFirst(FILE_CUSTOM_HEADER_KEY);
    }

    public static String getName(FileUpload file) {
        return file.getHeaders().getFirst(FILE_CUSTOM_HEADER_NAME);
    }
}
