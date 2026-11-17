package bio.cosy.feddb.core.execution.message;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

public abstract class BaseExecutionMessageAO<Entity extends BaseExecutionMessageEntity> implements PanacheRepository<Entity> {

}
