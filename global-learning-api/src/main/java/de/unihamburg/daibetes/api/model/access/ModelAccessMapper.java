package de.unihamburg.daibetes.api.model.access;

import bio.cosy.feddb.core.api.model.ModelAccessDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface ModelAccessMapper extends BaseMapper<ModelAccessDTO, ModelAccessEntity> {


    @Mappings({
            @Mapping(target = "modelId", source = "model.id"),
    })
    ModelAccessDTO entityToDto(ModelAccessEntity entity);

    @Mappings({
            @Mapping(target = "model.id", source = "modelId"),
    })
    ModelAccessEntity dtoToEntity(ModelAccessDTO dto);

}
