package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineStepDTO;
import bio.cosy.feddb.core.api.pipeline.StepName;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.build.pipeline.PipelineDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PipelineStepBO extends BaseBo<PipelineStepDTO, PipelineStepEntity, PipelineStepAO, PipelineStepMapper> {

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
