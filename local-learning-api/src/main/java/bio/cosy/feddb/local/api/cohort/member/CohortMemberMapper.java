package bio.cosy.feddb.local.api.cohort.member;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface CohortMemberMapper extends BaseMapper<CohortMemberDTO, CohortMemberEntity> {

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
    })
    CohortMemberDTO entityToDto(CohortMemberEntity entity);

    @Mappings({
            @Mapping(target = "cohort.id", source = "cohortId")
    })
    CohortMemberEntity dtoToEntity(CohortMemberDTO sourceCode);

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true)
    })
    CohortMemberDTO crudDtoToDto(CohortMemberCreateDTO sourceCode);

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "cohort", ignore = true)
    })
    CohortMemberEntity crudDtoToEntity(CohortMemberCreateDTO sourceCode);

}
