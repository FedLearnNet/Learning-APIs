package de.unihamburg.daibetes.api.build.pipeline;

import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;

public record PipelinePublishEvent(Long pipelineId, Long appVersionId, FederatedAppPublishDTO publishRequest) {
}
