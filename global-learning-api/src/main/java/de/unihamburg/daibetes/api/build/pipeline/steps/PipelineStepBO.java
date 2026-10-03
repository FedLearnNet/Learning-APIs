package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineStepDTO;
import bio.cosy.feddb.core.api.pipeline.StepName;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.build.pipeline.PipelineDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;

@ApplicationScoped
public class PipelineStepBO extends BaseBo<PipelineStepDTO, PipelineStepEntity, PipelineStepAO, PipelineStepMapper> {

    public PipelineDTO createSteps(PipelineDTO dto) {
        ArrayList<PipelineStepDTO> steps = new ArrayList<>();
        for (StepName stepName : StepName.values()) {
            PipelineStepDTO step = new PipelineStepDTO();
            step.setName(stepName);
            step.setStepStatus(PipelineStatus.PENDING);
            step.setPipelineId(dto.getId());
            steps.add(create(step));
        }
        dto.setPipelineSteps(steps);
        return dto;
    }
}
