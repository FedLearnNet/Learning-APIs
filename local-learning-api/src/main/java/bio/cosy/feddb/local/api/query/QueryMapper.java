package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<LocalQueryDTO, QueryEntity> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "query", source = "queryString", qualifiedByName = "jsonStringToQueryItems"),
            @Mapping(target = "enhancedQuery", ignore = true),
            @Mapping(target = "queriedCohortIds", ignore = true)
    })
    LocalQueryDTO entityToDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "queryString", source = "query", qualifiedByName = "queryItemsToJsonString"),
            @Mapping(target = "patients", ignore = true)
    })
    QueryEntity dtoToEntity(LocalQueryDTO dto);


    @Mappings({
            @Mapping(target = "schemaNodes", source = "schemaNodes")
    })
    LocalQueryItemDTO localQueryEnhanced(QueryItemDTO query,
                                         List<LocalSchemaNodeDTO> schemaNodes);


    @Named("jsonStringToQueryItems")
    default List<QueryItemDTO> jsonStringToQueryItems(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructCollectionType(List.class,
                    QueryItemDTO.class));
        } catch (IOException e) {
            Log.error("Error parsing JSON string to QueryItemDTO list", e);
            return new ArrayList<>();
        }
    }

    @Named("queryItemsToJsonString")
    default String queryItemsToJsonString(List<QueryItemDTO> queryItems) {
        if (queryItems == null || queryItems.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(queryItems);
        } catch (JsonProcessingException e) {
            Log.error("Error converting QueryItemDTO list to JSON string", e);
            return "";
        }
    }

}
