package de.unihamburg.daibetes.api.runs.test;

import bio.cosy.feddb.core.api.app.config.ToolConfigModeType;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DummyDataRequestDTO;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.services.DataModelerDataTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class TestRunBO extends BaseBo<TestRunDTO, TestRunEntity, TestRunAO, TestRunMapper> {

    @Inject
    @RestClient
    DataModelerDataTypeService dataModelerService;

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    ProjectBO projectBO;

    @Inject
    FederatedAppConfigBO federatedAppConfigBO;

    // Single source of truth for the overhead flag; runtime is always exposed regardless.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    public List<TestRunDTO> findByAppId(Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, id)) {
            List<TestRunDTO> dtos = mapper.entitiesToDtos(ao.findByAppId(id));
            dtos.forEach(this::applyOverheadVisibility);
            return dtos;
        }
        throw new ForbiddenException();
    }

    public TestRunDTO getById(Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, id)) {
            return applyOverheadVisibility(mapper.entityToDto(ao.findById(id)));
        }
        throw new ForbiddenException();
    }

    /** Strips the overhead metrics from the DTO's meta.timings when the overhead flag is disabled. */
    private TestRunDTO applyOverheadVisibility(TestRunDTO dto) {
        if (dto != null && dto.getMeta() != null) {
            dto.getMeta().withOverheadVisibility(overheadEnabled);
        }
        return dto;
    }

    public StartRunDTO startTest(Long appId, TestRunCreateDTO createDTO) {

        ToolConfigsDTO config = federatedAppConfigBO.findByAppVersionId(createDTO.getFederatedAppVersionId());
        if (config == null) {
            throw new NotFoundException("No config found for app version");
        }
        TestRunDTO dto = new TestRunDTO();
        dto.setFederatedAppId(appId);
        dto.setFederatedAppVersionId(createDTO.getFederatedAppVersionId());
        dto.setStatus(RunStatusTypes.PENDING);
        dto.setHyperParams(createDTO.getHyperParams());


        if (createDTO.getProjectId() != null) {
            LinkedHashMap<String, Object> input = new LinkedHashMap<>();

            config.getInput().stream()
                    .filter(inputConfig -> ToolConfigModeType.isTraining(inputConfig.getMode()))
                    .map(ToolInputConfigDTO::getVariableName)
                    .filter(Objects::nonNull)
                    .forEach(variableName -> input.put(variableName, null));

            ProjectDetailDTO project = projectBO.getById(createDTO.getProjectId());
            List<PatientExportFeatureDTO> features = new ArrayList<>();
            if (project.getExportConfig() != null && project.getExportConfig().getFeatures() != null) {
                features = project.getExportConfig().getFeatures();
            }
            if (!input.isEmpty()) {
                String firstKey = input.keySet().stream().findFirst().get();
                DummyDataRequestDTO request = new DummyDataRequestDTO(features, 100, false, false);
                try (Response data = dataModelerService.generateDummyData(request).await().indefinitely()) {
                    input.put(firstKey, data.readEntity(List.class).toString());
                }
            }
            dto.setInputData(input);
        } else {
            dto.setInputFilePaths(createDTO.getInputFilePaths());
        }
        TestRunDTO createdDto = create(dto);
        return mapper.dtoToStartDto(createdDto);
    }

    public TestRunDTO updateTest(UpdateRunDTO updateTest) {
        TestRunDTO dto = getById(updateTest.getRunId());
        dto.setStatus(updateTest.getStatus());
        dto.setError(updateTest.getError());
        return update(dto);
    }

    public TestRunDTO uploadData(OutputRunDataDTO finishTest) {
        TestRunDTO dto = getById(finishTest.getRunId());
        dto.setOutputData(finishTest.getOutputData());
        return update(dto);
    }

    public TestRunDTO finishTest(FinishRunDTO finishTest) {
        TestRunDTO dto = getById(finishTest.getRunId());
        dto.setStatus(RunStatusTypes.FINISHED);
        // Persist the wrapper-measured metadata. Overhead is stored whenever the wrapper reports it;
        // the config flag only governs whether it is later exposed (see the read methods).
        dto.setMeta(finishTest.getMeta());
        return applyOverheadVisibility(update(dto));
    }

    public List<TestRunDTO> closeAllTestCauseOfDisconnecting(Long appId) {
        List<TestRunEntity> runningTests = ao.findAllRunningByAppId(appId);
        return mapper.entitiesToDtos(runningTests.stream()
                .peek(testRun -> {
                    testRun.setStatus(RunStatusTypes.ERROR);
                    testRun.setError("AppEngine disconnected via run");
                    ao.persist(testRun);
                }));

    }
}
