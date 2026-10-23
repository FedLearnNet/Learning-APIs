package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.core.api.app.FederatedAppType;

public record StepResultContext(
        Long stepId,
        Long workflowNodeId,
        Integer executionOrder,
        Long experimentId,
        Long appVersionId,
        String clinicId,
        String globalFLExperimentUniqueId,
        boolean modelUploader,
        boolean modelCanBePublic,
        FederatedAppType appType
) {
}
