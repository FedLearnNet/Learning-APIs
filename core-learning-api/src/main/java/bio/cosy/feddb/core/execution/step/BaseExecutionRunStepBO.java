package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.base.BaseBo;

public abstract class BaseExecutionRunStepBO<
        DTO extends BaseExecutionRunStepDTO,
        Entity extends BaseExecutionRunStepEntity<?, ?, ?>,
        AO extends BaseExecutionRunStepAO<Entity>,
        Mapper extends BaseExecutionRunStepMapper<DTO, Entity>>
        extends BaseBo<DTO, Entity, AO, Mapper> {
}
