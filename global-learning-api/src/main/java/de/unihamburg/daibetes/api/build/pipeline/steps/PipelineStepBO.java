package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.build.pipeline.PipelineDTO;
import de.unihamburg.daibetes.api.build.pipeline.PipelineEntity;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import de.unihamburg.daibetes.api.build.pipeline.PipelineStatusUpdateDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Date;

@ApplicationScoped
public class PipelineStepBO extends BaseBo<PipelineStepDTO, PipelineStepEntity, PipelineStepAO, PipelineStepMapper> {


    public void updateStep(PipelineEntity pipeline, PipelineStatusUpdateDTO dto) {
        PipelineStepEntity step = pipeline.getSteps().stream()
                .filter(s -> s.getName().equals(dto.getStepName()))
                .findFirst().orElseThrow(() -> new NotFoundException("Step not found: " + dto.getStepName()));


        step.setStepStatus(dto.getStatus());
        if (dto.getLogs() != null && !dto.getLogs().isBlank()) {
            step.appendLog(dto.getLogs());
        }
        if (dto.getProgress() != null) {
            step.setProgress(dto.getProgress());
        }

        //first time the step is started
        if (step.getStartedAt() == null) {
            step.setStartedAt(new Date());
        }

        if (dto.getStatus().equals(PipelineStatus.FAILED)) {
            if (dto.getErrorCode() != null) {
                step.setErrorCode(dto.getErrorCode());
            }
            if (dto.getErrorCode() != null) {
                step.setErrorMessage(dto.getErrorMessage());
            }
            step.setFinishedAt(new Date());
        }

        if (dto.getStatus().equals(PipelineStatus.SUCCESS)) {
            step.setFinishedAt(new Date());
        }
        if (dto.getStatus().equals(PipelineStatus.WARNING)) {
            step.setFinishedAt(new Date());
        }
    }

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
