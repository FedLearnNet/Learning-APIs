package bio.cosy.feddb.core.execution.run.dto;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class OutputRunDataDTO {

    private Long runId;
    private LinkedHashMap<String, Object> outputData;
}
