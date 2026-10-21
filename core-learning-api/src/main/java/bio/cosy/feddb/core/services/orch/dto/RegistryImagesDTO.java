package bio.cosy.feddb.core.services.orch.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistryImagesDTO {
    private String registry;
    private List<RegistryRepositoryDTO> repositories;
}
