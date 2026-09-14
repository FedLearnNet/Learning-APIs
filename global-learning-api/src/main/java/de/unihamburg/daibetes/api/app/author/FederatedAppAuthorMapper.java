package de.unihamburg.daibetes.api.app.author;

import bio.cosy.feddb.core.api.app.FederatedAppAuthorDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppAuthorMapper extends BaseMapper<FederatedAppAuthorDTO, FederatedAppAuthorEntity> {

    @Mapping(target = "federatedAppId", source = "federatedApp.id")
    FederatedAppAuthorDTO entityToDto(FederatedAppAuthorEntity entity);

    @Mapping(target = "federatedApp.id", source = "federatedAppId")
    FederatedAppAuthorEntity dtoToEntity(FederatedAppAuthorDTO sourceCode);
}
