package de.unihamburg.daibetes.execution.federation;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.run.types.FederatedParticipantType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionParticipantDTO extends BaseDTO {
    private Long runId;
    private String participantId;
    private FederatedParticipantType role;
    private boolean coordinator;
    private Boolean modelCanBePublic;
    private LinkedHashMap<String, String> inputFilePaths;
    private RunStatusTypes status;
    private String currentNodeId;
    private ProjectStatus stepStatus;
}
