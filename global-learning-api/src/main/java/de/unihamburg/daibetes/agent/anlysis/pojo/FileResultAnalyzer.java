package de.unihamburg.daibetes.agent.anlysis.pojo;

import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import lombok.Data;

import java.util.List;

@Data
public class FileResultAnalyzer {
    private String toolName;
    private String toolDescription;

    private String outputName;
    private String outputDescription;

    private List<ResultAnalyzerHyperParam> hyperParams = List.of();
    private List<ResultAnalyzerHyperParam> inputs = List.of();
    private FileContentDTO fileContent;
    private FileProfile fileProfile;
    private String fileType;
}
