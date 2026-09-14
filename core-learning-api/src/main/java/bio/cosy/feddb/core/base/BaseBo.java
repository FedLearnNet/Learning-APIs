package bio.cosy.feddb.core.base;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public abstract class BaseBo<Dto extends BaseDTO, Entity extends BaseEntity, Ao extends PanacheRepository<Entity>,
        Mapper extends BaseMapper<Dto, Entity>> {

    @Inject
    public Ao ao;
    @Inject
    public Mapper mapper;


    public Dto getById(Long id) {
        Optional<Entity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        Dto dto = mapper.entityToDto(entity.get());
        return dto;
    }

    public List<Dto> getAll() {
        return mapper.entitiesToDtos(ao.findAll().stream());
    }

    public Dto create(Dto request) {
        Entity entity = mapper.dtoToEntity(request);
        ao.persist(entity);
        Dto dto = mapper.entityToDto(entity);
        return dto;
    }


    public Dto update(Long id, Dto request) {
        if (!request.getId().equals(id)) {
            throw new NotAllowedException(String.format("ID %s not matching with the object", id));
        }
        return update(request);
    }

    public Dto update(Dto request) {
        Long id = request.getId();
        Entity entity = mapper.dtoToEntity(request);
        return update(id, request.getVersion(), entity);
    }

    public Entity updateEntity(Dto request) {
        Long id = request.getId();
        Entity entity = mapper.dtoToEntity(request);
        return updateEntity(id, request.getVersion(), entity);
    }

    public Entity updateEntity(Long id, Long version, Entity entity) {
        Optional<Entity> foundEntityOptional = ao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
        if (foundEntityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }

        Entity foundEntity = foundEntityOptional.get();

        if (version == null) {
            throw new ClientErrorException("Dto need an version to check Version-OptimisticLock", Response.Status.FORBIDDEN);
        }
        if (entity.getVersion() == null || !entity.getVersion().equals(foundEntity.getVersion())) {
            throw new ClientErrorException("The entity was updated by another transaction", Response.Status.FORBIDDEN);
        }
        entity = mergeEntity(foundEntity, entity);
        //update entity in database
        entity.setUpdatedAt(new Date());
        return ao.getEntityManager().merge(entity);
    }

    public Entity mergeEntity(Entity foundEntity, Entity entity) {
        return entity;
    }

    public Dto update(Long id, Long version, Entity entity) {
        Entity mergedEntity = this.updateEntity(id, version, entity);
        return mapper.entityToDto(mergedEntity);
    }

    public void deleteById(Long id) {
        ao.deleteById(id);
    }


    public void setBaseValuesNull(BaseEntity entity) {
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);
        entity.setId(null);
        entity.setVersion(null);
    }

    public void setBaseValuesNull(BaseDTO dto) {
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setId(null);
        dto.setVersion(null);
    }

    public Dto entityToDto(Entity entity) {
        return mapper.entityToDto(entity);
    }

    public Entity dtoToEntity(Dto dto) {
        return mapper.dtoToEntity(dto);
    }
}
