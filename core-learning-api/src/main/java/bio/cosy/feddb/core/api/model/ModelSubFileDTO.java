package bio.cosy.feddb.core.api.model;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModelSubFileDTO extends BaseDTO {
    private Long modelSubId;
    private FileDTO file;
}
