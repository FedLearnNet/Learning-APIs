package de.unihamburg.daibetes.agent.audit.pojo;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolLlmAuditInput {
    private Long auditId;
    private String appName;
    private String appType;
    private String version;
    private String shortDescription;
    private String sourceUrl;
    private String commitHash;
    private boolean needsInternetAccess;
    private boolean needsHostAccess;
    private boolean supportsFederatedLearning;
    private List<String> inputs;
    private List<String> outputs;
    private List<String> hyperParams;
    private AppPublishInfoDTO publishInfo;
}
