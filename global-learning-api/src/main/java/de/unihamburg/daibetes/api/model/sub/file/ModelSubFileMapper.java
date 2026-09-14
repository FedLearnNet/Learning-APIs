package de.unihamburg.daibetes.api.model.sub.file;

import bio.cosy.feddb.core.api.model.ModelSubFileDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface ModelSubFileMapper extends BaseMapper<ModelSubFileDTO, ModelSubFileEntity> {

    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "file", expression = "java(fileMapper.entityToDto(entity.getFile()))"),
            @Mapping(target = "modelSubId", source = "modelSub.id")
    })
    ModelSubFileDTO entityToDto(ModelSubFileEntity entity);

    @Mappings({
            @Mapping(target = "file", expression = "java(fileMapper.dtoToEntity(dto.getFile()))"),
            @Mapping(target = "modelSub.id", source = "modelSubId")
    })
    ModelSubFileEntity dtoToEntity(ModelSubFileDTO dto);


}
