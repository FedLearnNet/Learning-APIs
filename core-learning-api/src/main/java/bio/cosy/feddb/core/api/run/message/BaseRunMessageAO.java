package bio.cosy.feddb.core.api.run.message;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

public abstract class BaseRunMessageAO<Entity extends BaseRunMessageEntity> implements PanacheRepository<Entity> {

}
