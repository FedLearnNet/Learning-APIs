package de.unihamburg.daibetes.api.development.run;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class TestRunCreateDTO {
    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
    // Optinal input, if e.g. start as test container and not as native
    private LinkedHashMap<String, Object> inputs;
    private Long projectId;
    private Long federatedAppVersionId;
}
