package bio.cosy.feddb.core.services.orch.dto;

import lombok.Data;

@Data
public class RamComputingInfoDTO {
    private double totalMemoryGb;
    private double usedMemoryGb;
    private double availableMemoryGb;
}