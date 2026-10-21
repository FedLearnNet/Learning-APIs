package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.base.BaseBo;

public abstract class BaseExecutionAttemptBO<
        DTO extends BaseExecutionAttemptDTO,
        Entity extends BaseExecutionAttemptEntity<?>,
        AO extends BaseExecutionAttemptAO<Entity>,
        Mapper extends BaseExecutionAttemptMapper<DTO, Entity>>
        extends BaseBo<DTO, Entity, AO, Mapper> {
}
