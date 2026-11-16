package de.unihamburg.daibetes.api.project.training.result.response;

import bio.cosy.feddb.core.api.socket.RunMetricsResponseClientDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.project.training.result.RunResultRequestEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RunResultResponseBO extends BaseBo<RunResultResponseDTO, RunResultResponseEntity, RunResultResponseAO, RunResultResponseMapper> {

    public void saveResponse(RunMetricsResponseClientDTO request, RunResultRequestEntity metricsRequest) {
        RunResultResponseEntity entity = new RunResultResponseEntity();
        entity.setRandomClinicId(request.getRandomClinicId());
        entity.setMetrics(request.getMetrics());
        entity.setRequest(metricsRequest);
        ao.persist(entity);
    }

    public List<RunResultResponseDTO> listForMetricsRequest(Long metricsRequestId) {
        return mapper.entitiesToDtos(ao.listForMetricsRequest(metricsRequestId));
    }
}
