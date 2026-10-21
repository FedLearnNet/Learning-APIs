package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.base.BaseBo;

public abstract class BaseExecutionRunBO<DTO extends BaseExecutionRunDTO,
        Entity extends BaseExecutionRunEntity<?>,
        AO extends BaseExecutionRunAO<Entity>,
        Mapper extends BaseExecutionEntityMapper<DTO, Entity>> extends BaseBo<
        DTO,
        Entity,
        AO,
        Mapper> {
}
