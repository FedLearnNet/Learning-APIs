package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogListResponseDTO {
    private List<ConnectorRunStepDTO> steps = new ArrayList<>();
}
