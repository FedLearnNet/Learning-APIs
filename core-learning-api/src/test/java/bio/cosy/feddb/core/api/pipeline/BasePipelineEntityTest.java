package bio.cosy.feddb.core.api.pipeline;

import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BasePipelineEntityTest {

    @Getter
    static class Pipeline extends BasePipelineEntity<BasePipelineStepEntity<Pipeline>> {
        private final List<BasePipelineStepEntity<Pipeline>> steps = List.of(
                step(StepName.PREFLIGHT), step(StepName.BUILD_IMAGE), step(StepName.SCAN_IMAGE));
    }

    private static BasePipelineStepEntity<Pipeline> step(StepName name) {
        BasePipelineStepEntity<Pipeline> step = new BasePipelineStepEntity<>();
        step.setName(name);
        step.setStepStatus(PipelineStatus.PENDING);
        return step;
    }

    private static PipelineStatusUpdateDTO update(StepName name, PipelineStatus status, String logs) {
        PipelineStatusUpdateDTO dto = new PipelineStatusUpdateDTO();
        dto.setStepName(name);
        dto.setStatus(status);
        dto.setLogs(logs);
        return dto;
    }

    @Test
    void succeedsOnceEveryStepButThePreflightSucceededOrWarned() {
        Pipeline pipeline = new Pipeline();
        pipeline.apply(update(StepName.BUILD_IMAGE, PipelineStatus.RUNNING, "started"));
        pipeline.apply(update(StepName.BUILD_IMAGE, PipelineStatus.SUCCESS, "built"));
        assertEquals(PipelineStatus.RUNNING, pipeline.getPipelineStatus());
        pipeline.apply(update(StepName.SCAN_IMAGE, PipelineStatus.WARNING, null));
        assertEquals(PipelineStatus.SUCCESS, pipeline.getPipelineStatus());
        assertEquals("started\nbuilt", pipeline.getSteps().get(1).getLogs());
        assertNotNull(pipeline.getSteps().get(1).getFinishedAt());
    }

    @Test
    void failsAsSoonAsOneStepFailed() {
        Pipeline pipeline = new Pipeline();
        pipeline.apply(update(StepName.BUILD_IMAGE, PipelineStatus.FAILED, null));
        assertEquals(PipelineStatus.FAILED, pipeline.getPipelineStatus());
        assertThrows(RuntimeException.class, () -> pipeline.apply(update(StepName.PUSH_IMAGE, PipelineStatus.RUNNING, null)));
    }
}
