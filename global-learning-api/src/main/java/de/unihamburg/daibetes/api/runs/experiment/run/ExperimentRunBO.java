package de.unihamburg.daibetes.api.runs.experiment.run;

import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import bio.cosy.feddb.core.api.run.FinishRunDTO;
import bio.cosy.feddb.core.api.run.OutputRunDataDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.UpdateRunDTO;
import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

@ApplicationScoped
public class ExperimentRunBO extends BaseBo<ExperimentRunDTO, ExperimentRunEntity, ExperimentRunAO, ExperimentRunMapper> {


    @Inject
    FederatedAppAuthorBO authorBO;

    // Single source of truth for the overhead flag; runtime is always exposed regardless.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    public ExperimentRunDTO updateTest(UpdateRunDTO updateTest) {
        Optional<ExperimentRunEntity> runEntityOptional = ao.findByIdOptional(updateTest.getRunId());
        if(runEntityOptional.isEmpty()){
            throw new NotFoundException("Run not found");
        }
        ExperimentRunEntity runEntity = runEntityOptional.get();
        runEntity.setStatus(updateTest.getStatus());
        runEntity.setError(updateTest.getError());
        return mapper.entityToDto(runEntity);
    }

    public ExperimentRunDTO uploadData(OutputRunDataDTO finishTest){
        Optional<ExperimentRunEntity> runEntityOptional = ao.findByIdOptional(finishTest.getRunId());
        if(runEntityOptional.isEmpty()){
            throw new NotFoundException("Run not found");
        }
        ExperimentRunEntity runEntity = runEntityOptional.get();
        String output = this.mapper.hyperparamsToJsonStringAllowError(finishTest.getOutputData());
        runEntity.setOutput(output);
        return mapper.entityToDto(runEntity);
    }


    public ExperimentRunDTO finishTest(FinishRunDTO finishTest){
        Optional<ExperimentRunEntity> runEntityOptional = ao.findByIdOptional(finishTest.getRunId());
        if(runEntityOptional.isEmpty()){
            throw new NotFoundException("Run not found");
        }
        ExperimentRunEntity runEntity = runEntityOptional.get();
        runEntity.setStatus( RunStatusTypes.FINISHED);
        // Persist the wrapper-measured metadata (managed entity, flushed on tx commit).
        runEntity.setMeta(finishTest.getMeta());
        return applyOverheadVisibility(mapper.entityToDto(runEntity));
    }

    public List<ExperimentRunDTO> findByExperimentId(Long id) {
        List<ExperimentRunDTO> dtos = mapper.entitiesToDtos(ao.findByExperimentId(id));
        dtos.forEach(this::applyOverheadVisibility);
        return dtos;
    }

    /** Strips the overhead metrics from the DTO's meta.timings when the overhead flag is disabled. */
    private ExperimentRunDTO applyOverheadVisibility(ExperimentRunDTO dto) {
        if (dto != null && dto.getMeta() != null) {
            dto.getMeta().withOverheadVisibility(overheadEnabled);
        }
        return dto;
    }

    public Optional<UpdateExperimentRunDTO> findNextByExperimentId(Long appId) {
        return ao.findNextByAppId(appId).map(mapper::entityToUpdateDto);
    }

    public ExperimentRunDTO getById(Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, id)) {
            return applyOverheadVisibility(mapper.entityToDto(ao.findById(id)));
        }
        throw new ForbiddenException();
    }

    public ExperimentRunDTO create(Long experimentId, LinkedHashMap<String, Object> hyperparams) {
        ExperimentRunDTO dto = new ExperimentRunDTO();
        dto.setExperimentId(experimentId);
        dto.setStatus(RunStatusTypes.PENDING);
        dto.setHyperParams(hyperparams);
        dto.setColor(generateRandomColor());
        String uniqueID = UUID.randomUUID().toString();
        dto.setName("Run " + uniqueID);
        return create(dto);
    }

    private String generateRandomColor() {
        Random random = new Random();
        int nextInt = random.nextInt(0xffffff + 1);
        return String.format("#%06x", nextInt);
    }
}
