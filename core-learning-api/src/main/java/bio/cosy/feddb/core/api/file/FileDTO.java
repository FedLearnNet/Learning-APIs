package bio.cosy.feddb.core.api.file;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FileDTO extends BaseDTO {
    private String fileName;
    private String contentType;
    private String secret;
    private String downloadUrl;
    private long size;

    @JsonIgnore
    public String getDownloadContentUrlSecret() {
        if(getDownloadUrl() == null || getSecret() == null) {
            return null;
        }
        return getDownloadUrl() + "/download?secret=" + getSecret();
    }
}
