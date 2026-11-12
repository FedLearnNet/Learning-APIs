package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemComputingInfoReducedDTO {
    private Cpu cpuDetails;
    private Ram ramDetails;
    private List<Gpu> gpuHardwareInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cpu {
        private String modelName;
        private int logicalCores;
        private String architecture;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ram {
        private double totalMemoryGb;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Gpu {
        private String name;
        private String totalMemory;
    }
}
