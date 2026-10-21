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
import java.util.List;

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
        List<StepName> stepNames = switch (dto.getRunMode()) {
            case ALL -> List.of(StepName.PREFLIGHT, StepName.FETCH_CONFIG, StepName.CLONE_REPO,
                    StepName.FETCH_FILES, StepName.SAST, StepName.BUILD_IMAGE, StepName.SCAN_IMAGE,
                    StepName.MALWARE_CHECK, StepName.RUN_PYTEST, StepName.PUSH_IMAGE, StepName.COLLECT_SUMMARY);
            case LOCAL_BUILD -> List.of(StepName.PREFLIGHT, StepName.FETCH_CONFIG, StepName.FETCH_FILES,
                    StepName.SAST, StepName.BUILD_IMAGE, StepName.SCAN_IMAGE, StepName.MALWARE_CHECK,
                    StepName.RUN_PYTEST, StepName.COLLECT_SUMMARY);
            case SCAN_ONLY -> List.of(StepName.PREFLIGHT, StepName.FETCH_CONFIG, StepName.PULL_IMAGE,
                    StepName.SCAN_IMAGE, StepName.COLLECT_SUMMARY);
        };
        for (StepName stepName : stepNames) {
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
