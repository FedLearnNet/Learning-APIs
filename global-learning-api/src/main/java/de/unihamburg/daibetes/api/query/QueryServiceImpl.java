package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class QueryServiceImpl implements QueryService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    QueryBO bo;

    @Inject
    @Channel("queries")
    Multi<QueryDTO> queries;


    @Override
    public List<QueryDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.getAll(keycloakId);
    }

    @Override
    public Multi<QueryDTO> listSSE() {
        String keycloakId = userIdentity.getKeycloakId();
        return queries
                .filter(queryDTO -> queryDTO.getKeycloakId().equals(keycloakId))
                .filter(queryDTO -> bo.isLatestVersion(queryDTO.getId(), keycloakId));
    }

    @Override
    public List<String> getAllClients() {
        return bo.getConnections();
    }

    @Override
    public QueryDetailDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.getDetailById(id, keycloakId);
    }

    @Override
    @Transactional
    public Response create(QueryCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        QueryDTO createdDTO = bo.create(createDTO, keycloakId);
        return Response.status(Response.Status.CREATED)  // 201 status
                .entity(createdDTO)
                .build();
    }

    @Override
    @Transactional
    public QueryDTO update(Long id, QueryDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.update(id, updateDTO, keycloakId);
    }

    @Override
    @Transactional
    public QueryDTO fireQuery(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        Set<String> roles = userIdentity.getRoles();
        return bo.fireQuery(id, keycloakId, roles);
    }

    @Override
    @Transactional
    public QueryDTO fireDataStatistics(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.fireDataStatistics(id, keycloakId);
    }

    @Override
    @Transactional
    public QueryDTO createAndRun(QueryCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        Set<String> roles = userIdentity.getRoles();
        return bo.createAndRun(createDTO, keycloakId, roles);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.deleteById(id, keycloakId);
        return Response.ok().build();
    }
}
