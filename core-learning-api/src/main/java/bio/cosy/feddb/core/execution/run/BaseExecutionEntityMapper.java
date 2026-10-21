package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.base.BaseMapper;

public interface BaseExecutionEntityMapper<
        Dto extends BaseExecutionRunDTO, Entity extends BaseExecutionRunEntity<?>>
        extends BaseMapper<Dto, Entity> {
}
