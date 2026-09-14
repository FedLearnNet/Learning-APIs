package bio.cosy.feddb.local.api.cohort.permission;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface PermissionMapper extends BaseMapper<PermissionDTO, PermissionEntity> {

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
    })
    PermissionDTO entityToDto(PermissionEntity entity);

    @Mappings({
            @Mapping(target = "cohort.id", source = "cohortId")
    })
    PermissionEntity dtoToEntity(PermissionDTO dto);

}
