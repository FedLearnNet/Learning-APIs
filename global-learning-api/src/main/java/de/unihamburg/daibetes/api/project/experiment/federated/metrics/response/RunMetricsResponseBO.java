package de.unihamburg.daibetes.api.project.experiment.federated.metrics.response;

import bio.cosy.feddb.core.api.socket.RunMetricsResponseClientDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.RunMetricsRequestEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RunMetricsResponseBO extends BaseBo<RunMetricsResponseDTO, RunMetricsResponseEntity, RunMetricsResponseAO, RunMetricsResponseMapper> {

    public void saveResponse(RunMetricsResponseClientDTO request, RunMetricsRequestEntity metricsRequest) {
        RunMetricsResponseEntity entity = new RunMetricsResponseEntity();
        entity.setRandomClinicId(request.getRandomClinicId());
        entity.setMetrics(request.getMetrics());
        entity.setRequest(metricsRequest);
        ao.persist(entity);
    }

    public List<RunMetricsResponseDTO> listForMetricsRequest(Long metricsRequestId) {
        return mapper.entitiesToDtos(ao.listForMetricsRequest(metricsRequestId));
    }
}
