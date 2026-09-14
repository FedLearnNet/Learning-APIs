package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.local.api.cohort.statistics.entry.config.CustomStatisticConfig;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomStatisticDTO {
    @NotNull
    private Long dashboardId;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private CustomStatisticType type;

    @NotNull
    private CustomStatisticConfig config;
}
