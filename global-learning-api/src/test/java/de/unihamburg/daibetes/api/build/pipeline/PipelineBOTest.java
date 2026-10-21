package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.MalwareScanResultDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import bio.cosy.feddb.core.api.pipeline.SastScanResultDTO;
import bio.cosy.feddb.core.api.pipeline.SecurityScanResultDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineBOTest {

    @ParameterizedTest
    @MethodSource("scanOnlyResults")
    void scanOnlyFillsOmittedResultsWithoutOverwritingCurrentResults(
            PipelineType type, boolean hasCurrentSast, boolean hasCurrentMalware) {
        AppPublishInfoDTO previous = publishInfo();
        PipelineEntity pipeline = pipeline(type, PipelineRunMode.SCAN_ONLY, previous);
        AppPublishInfoDTO current = new AppPublishInfoDTO();
        current.setImageName(previous.getImageName());
        SecurityScanResultDTO vulnerability = new SecurityScanResultDTO();
        current.setVulnerabilityScanResult(vulnerability);
        SastScanResultDTO sast = new SastScanResultDTO();
        MalwareScanResultDTO malware = new MalwareScanResultDTO();
        if (hasCurrentSast) {
            current.setSastScanResult(sast);
        }
        if (hasCurrentMalware) {
            current.setMalwareScanResult(malware);
        }
        PipelineBO bo = pipelineBO(pipeline);

        bo.processSummary(1L, "secret", current);

        assertSame(current, pipeline.getPublishInfo());
        assertEquals(previous.getCommitHash(), current.getCommitHash());
        assertEquals(previous.getFilePaths(), current.getFilePaths());
        assertSame(hasCurrentSast ? sast : previous.getSastScanResult(), current.getSastScanResult());
        assertSame(hasCurrentMalware ? malware : previous.getMalwareScanResult(), current.getMalwareScanResult());
        assertSame(vulnerability, current.getVulnerabilityScanResult());
        assertEquals(previous.getImageName(), current.getImageName());
        assertEquals("published-tag", pipeline.getDockerTag());
        verify(bo.ao).persist(pipeline);
    }

    private static Stream<Arguments> scanOnlyResults() {
        return Stream.of(PipelineType.values()).flatMap(type -> Stream.of(
                Arguments.of(type, false, false),
                Arguments.of(type, true, false),
                Arguments.of(type, false, true),
                Arguments.of(type, true, true)));
    }

    @ParameterizedTest
    @EnumSource(PipelineType.class)
    void scanOnlyAcceptsSummaryWithoutPreviousPublishInfo(PipelineType type) {
        PipelineEntity pipeline = pipeline(type, PipelineRunMode.SCAN_ONLY, null);
        AppPublishInfoDTO current = new AppPublishInfoDTO();
        SecurityScanResultDTO vulnerability = new SecurityScanResultDTO();
        current.setVulnerabilityScanResult(vulnerability);

        pipelineBO(pipeline).processSummary(1L, "secret", current);

        assertSame(current, pipeline.getPublishInfo());
        assertSame(vulnerability, current.getVulnerabilityScanResult());
        assertNull(current.getCommitHash());
        assertNull(current.getFilePaths());
        assertNull(current.getSastScanResult());
        assertNull(current.getMalwareScanResult());
    }

    @ParameterizedTest
    @EnumSource(value = PipelineRunMode.class, names = {"ALL", "LOCAL_BUILD"})
    void buildSummaryDoesNotInheritPreviousResults(PipelineRunMode mode) {
        PipelineEntity pipeline = pipeline(PipelineType.MODEL, mode, publishInfo());
        AppPublishInfoDTO current = new AppPublishInfoDTO();
        current.setCommitHash("current-commit");

        pipelineBO(pipeline).processSummary(1L, "secret", current);

        assertSame(current, pipeline.getPublishInfo());
        assertEquals("current-commit", current.getCommitHash());
        assertEquals("current-commit", pipeline.getDockerTag());
        assertNull(current.getFilePaths());
        assertNull(current.getSastScanResult());
        assertNull(current.getMalwareScanResult());
        assertNull(current.getVulnerabilityScanResult());
    }

    private PipelineBO pipelineBO(PipelineEntity pipeline) {
        PipelineBO bo = new PipelineBO();
        bo.ao = mock(PipelineAO.class);
        bo.mapper = mock(PipelineMapper.class);
        when(bo.ao.findByIdAndSecretOptional(1L, "secret", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(Optional.of(pipeline));
        when(bo.mapper.entityToDto(pipeline)).thenReturn(new PipelineDTO());
        return bo;
    }

    private PipelineEntity pipeline(PipelineType type, PipelineRunMode mode, AppPublishInfoDTO previous) {
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setPipelineType(type);
        pipeline.setRunMode(mode);
        pipeline.setDockerTag("published-tag");
        if (type == PipelineType.MODEL) {
            ModelSubEntity model = new ModelSubEntity();
            model.setPublishInfo(previous);
            pipeline.setModelSub(model);
        } else {
            FederatedAppVersionEntity app = new FederatedAppVersionEntity();
            app.setPublishInfo(previous);
            pipeline.setAppVersion(app);
        }
        return pipeline;
    }

    private AppPublishInfoDTO publishInfo() {
        AppPublishInfoDTO previous = new AppPublishInfoDTO();
        previous.setCommitHash("published-commit");
        previous.setFilePaths(List.of("https://example.com/tool/blob/published-commit/data/model.bin"));
        previous.setSastScanResult(new SastScanResultDTO());
        previous.setMalwareScanResult(new MalwareScanResultDTO());
        previous.setVulnerabilityScanResult(new SecurityScanResultDTO());
        previous.setImageName("registry.example.com/tool:published-tag");
        return previous;
    }
}
