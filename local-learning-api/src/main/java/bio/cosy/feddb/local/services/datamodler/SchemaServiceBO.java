package bio.cosy.feddb.local.services.datamodler;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchemaServiceBO {
    @RestClient
    GlobalSchemaService globalSchemaService;

    @Inject
    GlobalSchemaServiceMapper schemaNodeMapper;

    @RestClient
    GlobalSchemaSubscriptionService globalSchemaSubscriptionService;

    public List<LocalSchemaRootNodeDTO> getGlobalRootNodes() {
        List<SchemaNodeDetailDTO> globalRootSchemas = globalSchemaService.getHead(null).await().indefinitely();
        // Map the global root schema information to local root schema information
        return globalRootSchemas.stream()
                .map(schemaNodeMapper::globalHeadRootToLocalRootDto)
                .collect(Collectors.toList());
    }

    public LocalSchemaRootNodeDTO getWholeSchemaByRootId(UUID id) {
        try {
            SchemaStructureDTO globalSchema = globalSchemaService.getSubStructure(id).await().indefinitely();
            // Map the global schema information to local schema information
            return schemaNodeMapper.globalInfoToLocalRoot(globalSchema);
        } catch (WebApplicationException wae) {
            // Map remote 404 to local NotFoundException
            if (wae.getResponse() != null && wae.getResponse().getStatus() == 404) {
                throw new NotFoundException("Schema not found");
            }
            // rethrow other web application exceptions
            throw wae;
        }
    }

    public LocalSchemaRootNodeDTO subscribe(UUID id) {
        try {
            SchemaStructureDTO globalSchema = globalSchemaSubscriptionService.subscribe(id).await().indefinitely();
            // Map the global schema information to local schema information
            return schemaNodeMapper.globalInfoToLocalRoot(globalSchema);
        } catch (WebApplicationException wae) {
            // Map remote 404 to local NotFoundException
            if (wae.getResponse() != null && wae.getResponse().getStatus() == 404) {
                throw new NotFoundException("Schema not found");
            }
            // rethrow other web application exceptions
            throw wae;
        }
    }
}
