package de.unihamburg.daibetes.api.app.tag;

import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppTagMapper extends BaseMapper<FederatedAppTagDTO, FederatedAppTagEntity> {

    @Mappings({
            @Mapping(target = "apps", ignore = true)
    })
    FederatedAppTagEntity dtoToEntity(FederatedAppTagDTO dto);
}
