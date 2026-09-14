package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorFilesMapper extends BaseMapper<ConnectorFilesDTO, ConnectorFilesEntity> {

    @Mappings({
            @Mapping(target = "downloadUrl", source = "entity", qualifiedByName = "getDownloadUrl"),
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "connectorId", source = "connector.id")
    })
    ConnectorFilesDTO entityToDto(ConnectorFilesEntity entity);

    @Mappings({
            @Mapping(target = "keycloakId", ignore = true),
            @Mapping(target = "largeObjectId", ignore = true),
            @Mapping(target = "cohort", source = "cohortId", qualifiedByName = "idToCohortEntity"),
            @Mapping(target = "connector", source = "connectorId", qualifiedByName = "idToConnectorEntity"),
            @Mapping(target = "uploadInfo", ignore = true),
            @Mapping(target = "uploadSettings", ignore = true),
            @Mapping(target = "previewData", ignore = true),
            @Mapping(target = "transformedStatisticsCacheKey", ignore = true),
            @Mapping(target = "transformedStatistics", ignore = true)
    })
    ConnectorFilesEntity dtoToEntity(ConnectorFilesDTO dto);

    @Mappings({
            @Mapping(target = "downloadUrl", source = "entity", qualifiedByName = "getDownloadUrl"),
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "connectorId", source = "connector.id"),
            @Mapping(target = "fileExists", ignore = true),
            @Mapping(target = "runs", ignore = true)
    })
    ConnectorFilesDetailDTO entityToDetailDto(ConnectorFilesEntity entity);


    @Named("getDownloadUrl")
    default String getDownloadUrl(ConnectorFilesEntity entity) {
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

            Long cohortId = entity.getCohort().getId();
            return baseUrl + "/cohorts/" + cohortId + "/files/" + entity.getId() + "/download";
        } catch (Exception ignored) {
            return "";
        }
    }

    @Named("idToCohortEntity")
    default CohortEntity idToCohortEntity(Long cohortId) {
        if (cohortId == null) {
            return null;
        }
        CohortEntity cohort = new CohortEntity();
        cohort.setId(cohortId);
        return cohort;
    }

    @Named("idToConnectorEntity")
    default ConnectorEntity idToConnectorEntity(Long connectorId) {
        if (connectorId == null) {
            return null;
        }
        ConnectorEntity connector = new ConnectorEntity();
        connector.setId(connectorId);
        return connector;
    }

}
