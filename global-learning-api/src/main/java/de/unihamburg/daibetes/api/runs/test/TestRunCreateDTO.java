package de.unihamburg.daibetes.api.runs.test;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class TestRunCreateDTO {
    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
    private Long projectId;
    private Long federatedAppVersionId;
}
