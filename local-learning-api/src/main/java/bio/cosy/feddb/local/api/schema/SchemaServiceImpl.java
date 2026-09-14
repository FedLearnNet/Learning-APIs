package bio.cosy.feddb.local.api.schema;

import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SchemaServiceImpl implements SchemaService {
    @Inject
    @RestClient
    GlobalSchemaService globalSchemaService;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    SchemaBO schemaBO;

    @Override
    public List<LocalSchemaRootNodeDTO> getGlobalRootNodes() {
        return schemaBO.loadRemoteAllHeads();
    }

    @Override
    public LocalSchemaRootNodeDTO getGlobalSchemaFormInfo(UUID id) {
        return schemaBO.loadRemoteGlobalSchemaById(id);
    }

    @Override
    public LocalSchemaNodeDTO getDetailById(Long id) {
        return schemaNodeBO.findById(id)
                .orElseThrow(() -> new NotFoundException("Local SchemaNode with ID " + id + " not found"));
    }

    @Override
    public List<LocalSchemaNodeDTO> getSchemaNodesForCohort(Long cohortId) {
        return schemaNodeBO.getSchemaNodesForCohort(cohortId);
    }

    @Override
    public List<LocalSchemaNodeDTO> getSchemaNodesForProject(Long projectId) {
        return schemaNodeBO.getSchemaNodesForProject(projectId);
    }

    @Override
    public LocalSchemaRootNodeDTO getSchemaNodesNestedForCohort(Long cohortId) {
        return schemaNodeBO.getSchemaNodesNestedForCohort(cohortId);
    }
}
