package bio.cosy.feddb.core.execution.file;

import bio.cosy.feddb.core.base.BaseMapper;

public interface BaseExecutionFileMapper<
        Dto extends BaseExecutionFileDTO,
        Entity extends BaseExecutionFileEntity<?, ?>> extends BaseMapper<Dto, Entity> {
}
