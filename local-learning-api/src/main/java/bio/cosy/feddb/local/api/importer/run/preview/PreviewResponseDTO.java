package bio.cosy.feddb.local.api.importer.run.preview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewResponseDTO {
    private List<String> jsons;

    private List<PreviewStageDTO> stages;

    public PreviewResponseDTO(List<String> jsons) {
        this.jsons = jsons;
    }
}
