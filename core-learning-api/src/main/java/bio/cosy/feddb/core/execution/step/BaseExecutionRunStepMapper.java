package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.base.BaseMapper;

public interface BaseExecutionRunStepMapper<
        Dto extends BaseExecutionRunStepDTO,
        Entity extends BaseExecutionRunStepEntity<?, ?, ?>> extends BaseMapper<Dto, Entity> {
}
