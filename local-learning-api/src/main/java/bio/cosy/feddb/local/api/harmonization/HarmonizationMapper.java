package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = QuarkusMappingConfig.class)
public interface HarmonizationMapper extends BaseMapper<HarmonizationDTO, HarmonizationEntity> {

    @Mapping(target = "keycloakId", ignore = true)
    HarmonizationEntity dtoToEntity(HarmonizationDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(HarmonizationDTO dto, @MappingTarget HarmonizationEntity entity);
}
