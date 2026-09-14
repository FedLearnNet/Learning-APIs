package de.unihamburg.daibetes.api.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.*;
import bio.cosy.feddb.core.api.datamodler.validation.ValidationResultDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.helper.RandomDataGenerator;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DataTypeServiceImpl implements DataTypeService {

    @Inject
    DataTypeBO dataTypeBO;

    @Inject
    RandomDataGenerator randomDataGenerator;

    @Override
    public Uni<PagedResponse<DataTypeNodeDTO>> list(UUID ontologyId, int page, int pageSize, String search) {
        return dataTypeBO.list(ontologyId, page, pageSize, search);
    }

    @Override
    public Uni<PagedResponse<DataTypeNodeDetailDTO>> listDetailed(UUID schemaId, List<UUID> dataTypeIds, int page, int pageSize) {
        return dataTypeBO.listDetailed(schemaId, dataTypeIds, page, pageSize);
    }

    @Override
    public Uni<List<DataTypeSubscriptionDTO>> listForQuery(List<UUID> ontologyIds) {
        return dataTypeBO.listForQuery(ontologyIds);
    }

    @Override
    public Uni<Response> generateDummyData(DummyDataRequestDTO request) {
        return dataTypeBO.generateDummyData(request)
                .onItem().transform(data -> {
                    if (data == null || data.isEmpty()) {
                        Map<String, String> body = Map.of("message", "No data found");
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(body)
                                .build();
                    }
                    if (request.isAsFile()) {

                        String csv = randomDataGenerator.toCsv(data);
                        return Response.ok(csv)
                                .type("text/csv")
                                .header("Content-Disposition", "attachment; filename=\"export.csv\"")
                                .build();
                    }

                    return Response.ok(data).build();
                });
    }

    @Override
    public Uni<DataTypeNodeDTO> create(DataTypeNodeDTO dto) {
        return dataTypeBO.create(dto);
    }

    @Override
    public Uni<DataTypeNodeDTO> getById(UUID id) {
        return dataTypeBO.getById(id);
    }

    @Override
    public Uni<List<DataTypeNodeDTO>> getByIds(List<UUID> ids) {
        return dataTypeBO.getByIds(ids);
    }

    @Override
    public Uni<ValidationResultDTO> checkValidations(UUID id, String value) {
        return dataTypeBO.checkValidations(id, value);
    }

    @Override
    public Uni<DataTypeNodeDTO> update(UUID id, DataTypeNodeDTO dto) {
        return dataTypeBO.update(id, dto);
    }

    @Override
    public Uni<Response> delete(UUID id) {
        return dataTypeBO.delete(id).onItem().transform(resp -> Response.ok().build());
    }
}
