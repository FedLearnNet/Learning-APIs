package bio.cosy.feddb.local.api.schema.ontology;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface OntologyMapper extends BaseMapper<OntologyDTO, OntologyEntity> {

    @Mappings({
    })
    OntologyDTO entityToDto(OntologyEntity entity);


    @Mappings({
            @Mapping(target = "schemaNodes", ignore = true)
    })
    OntologyEntity dtoToEntity(OntologyDTO dto);
}
