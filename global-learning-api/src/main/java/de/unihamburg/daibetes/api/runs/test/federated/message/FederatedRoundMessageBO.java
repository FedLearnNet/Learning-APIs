package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedRoundMessageBO extends BaseBo<FederatedRoundMessageDTO, FederatedRoundMessageEntity, FederatedRoundMessageAO, FederatedRoundMessageMapper> {

    public FederatedRoundMessageDTO create(FederatedRoundMessageCreateDTO dto) {
        return create(mapper.createDtoToEntity(dto, dto.getFederatedRunId()));
    }

    public List<FederatedRoundMessageDTO> listRoundMessages(Long runId) {
        return mapper.entitiesToDtos(ao.findByRun(runId));
    }

}
