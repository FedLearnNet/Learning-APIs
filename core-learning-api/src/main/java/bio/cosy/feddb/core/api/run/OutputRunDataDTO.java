package bio.cosy.feddb.core.api.run;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class OutputRunDataDTO {

    private Long runId;
    private LinkedHashMap<String, Object> outputData;
}
