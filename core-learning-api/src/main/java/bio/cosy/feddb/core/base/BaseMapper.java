package bio.cosy.feddb.core.base;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface BaseMapper <Dto extends BaseDTO, Entity extends BaseEntity>  {

    Dto entityToDto(Entity entity);

    Entity dtoToEntity(Dto sourceCode);

    List<Dto> entitiesToDtos(List<Entity> entity);
    List<Entity> dtosToEntities(List<Dto> dto);

    List<Dto> entitiesToDtos(Stream<Entity> entity);
    List<Dto> entitiesToDtos(Set<Entity> entity);

    List<Entity> dtosToEntities(Stream<Dto> dto);

}
