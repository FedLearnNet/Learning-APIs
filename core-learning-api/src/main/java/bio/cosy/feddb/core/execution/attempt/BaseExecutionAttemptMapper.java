package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.base.BaseMapper;

public interface BaseExecutionAttemptMapper<
        Dto extends BaseExecutionAttemptDTO,
        Entity extends BaseExecutionAttemptEntity<?, ?>> extends BaseMapper<Dto, Entity> {
}
