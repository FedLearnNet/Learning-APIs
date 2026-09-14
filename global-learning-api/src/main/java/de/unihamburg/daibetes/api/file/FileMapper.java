package de.unihamburg.daibetes.api.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface FileMapper extends BaseMapper<FileDTO, FileEntity> {

    @Mapping(target = "downloadUrl", source = "entity", qualifiedByName = "getDownloadUrl")
    FileDTO entityToDto(FileEntity entity);

    @Mapping(target = "largeObjectId", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    FileEntity dtoToEntity(FileDTO sourceCode);

    @Named("getDownloadUrl")
    default String getDownloadUrl(FileEntity entity) {
        try {
            HttpServerRequest req = CDI.current().select(HttpServerRequest.class).get();

            if (req == null || entity == null || entity.getId() == null) {
                return "";
            }

            String baseUrl = req.getHeader("Origin");
            if (StringUtils.isEmpty(baseUrl)) {
                baseUrl = req.scheme() + "://" + req.authority().host();
                if ("localhost".equals(req.authority().host())) {
                    baseUrl += ":" + req.authority().port();
                }
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            return baseUrl + "/files/" + entity.getId();
        } catch (Exception ignored) {
            return "http://localhost:8080/files/" + entity.getId();
        }
    }

}
