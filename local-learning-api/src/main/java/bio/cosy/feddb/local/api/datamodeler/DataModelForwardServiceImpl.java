package bio.cosy.feddb.local.api.datamodeler;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.services.datamodler.GlobalDataTypeService;
import bio.cosy.feddb.local.services.datamodler.GlobalOntologyService;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

@ApplicationScoped
public class DataModelForwardServiceImpl implements DataModelForwardService {

    @RestClient
    GlobalOntologyService globalOntologyService;

    @RestClient
    GlobalDataTypeService globalDataTypeService;

    @Override
    public PagedResponse<OntologyNodeDTO> searchOntologies(String search, int page, int pageSize) {
        return globalOntologyService.list(search, page, pageSize).await().indefinitely();
    }

    @Override
    public PagedResponse<DataTypeNodeDTO> getDatatypesForOntology(UUID ontologyId, String search, int page, int pageSize) {
        return globalDataTypeService.list(ontologyId, page, pageSize, search).await().indefinitely();
    }
}
