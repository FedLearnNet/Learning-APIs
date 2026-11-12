package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemComputingInfoReducedDTO {
    private CpuComputingInfoDTO cpuDetails;
    private Ram ramDetails;
    private List<Gpu> gpuHardwareInfo;

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
