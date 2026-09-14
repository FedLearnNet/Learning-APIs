package de.unihamburg.daibetes.api.umls.importer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UmlsImportDTO {
    @Schema(
            description = "Path to the MRCONSO UMLS concept file. Only needs to be provided for dev/test environments.",
            defaultValue = "MRCONSO.RRF"
    )
    @NotBlank(message = "Node file name must not be blank")
    private String nodeFileName = "MRCONSO.RRF";

    @Schema(
            description = "Path to the MRREL UMLS relationship file. Only needs to be provided for dev/test environments.",
            defaultValue = "MRREL.RRF"
    )
    @NotBlank(message = "Edge file name must not be blank")
    private String edgeFileName = "MRREL.RRF";
}
