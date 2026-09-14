package de.unihamburg.daibetes.api.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.*;
import bio.cosy.feddb.core.api.datamodler.validation.ValidationResultDTO;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.schema.SchemaBO;
import de.unihamburg.daibetes.api.schema.SchemaSubscriptions;
import de.unihamburg.daibetes.api.validation.ValidationBO;
import de.unihamburg.daibetes.helper.RandomDataGenerator;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class DataTypeBO {

    @Inject
    DataTypeDAO dataTypeDAO;

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    RandomDataGenerator randomDataGenerator;

    @Inject
    ValidationBO validationBO;

    @Inject
    SchemaBO schemaBO;

    public Uni<PagedResponse<DataTypeNodeDTO>> list(UUID ontologyId, int page, int pageSize, String search) {
        if (ontologyId == null) {
            return dataTypeDAO.getAll(page, pageSize, search)
                    .onItem().transform(el -> (DataTypeNodeDTO) el)
                    .collect().asList()
                    .onItem().transform(list -> new PagedResponse<>(list, page, pageSize, list.size()));
        }
        return dataTypeDAO.getAllByOntology(ontologyId, search)
                .onItem().transform(el -> (DataTypeNodeDTO) el)
                .collect().asList()
                .onItem().transform(list -> new PagedResponse<>(list, page, pageSize, list.size()));
    }

    public Uni<PagedResponse<DataTypeNodeDetailDTO>> listDetailed(UUID schemaId, List<UUID> dataTypeIds, int page, int pageSize) {
        if (schemaId != null) {
            return dataTypeDAO.getAllBySchema(schemaId, page, pageSize);
        }

        if (dataTypeIds != null && !dataTypeIds.isEmpty()) {
            return dataTypeDAO.getAllFilteredPaged(dataTypeIds, page, pageSize);
        }

        return dataTypeDAO.getAllPaged(page, pageSize, "");
    }


    public Uni<List<DataTypeNodeDTO>> getByIds(List<UUID> ids) {
        return dataTypeDAO.getByIds(ids);
    }

    public Uni<List<DataTypeSubscriptionDTO>> listForQuery(List<UUID> ontologyIds) {
        boolean filterOntologyEmpty = ontologyIds == null || ontologyIds.isEmpty();
        Uni<List<SchemaSubscriptions>> schemasUni = filterOntologyEmpty ?
                schemaBO.getAllSubscriptions() :
                ontologyDAO.getAllRelatedSchema(ontologyIds)
                        .collect().asList();

        return schemasUni.flatMap(schemaDataList -> {
            if (schemaDataList.isEmpty()) {
                return Uni.createFrom().item(List.<DataTypeSubscriptionDTO>of());
            }

            List<String> schemaIds = schemaDataList.stream()
                    .map(SchemaSubscriptions::getSchemaId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            Uni<List<DataTypeSchemaEdgeDTO>> dataTypesUni =
                    dataTypeDAO.getAllDataTypesForQuery(schemaIds)
                            .collect().asList();

            return dataTypesUni.map(dataTypeSchemaList -> {

                Map<String, List<String>> schemaToSubscriptions = schemaDataList.stream()
                        .collect(Collectors.toMap(
                                SchemaSubscriptions::getSchemaId,
                                s -> Optional.ofNullable(s.getSubscriptions()).orElse(List.of()),
                                (a, b) -> {
                                    List<String> merged = new ArrayList<>(a);
                                    merged.addAll(b);
                                    return merged;
                                }
                        ));

                Map<String, Set<String>> dataTypeToSubscriptions = new HashMap<>();

                for (DataTypeSchemaEdgeDTO ds : dataTypeSchemaList) {
                    String schemaId = ds.getSchemaId();
                    String dataTypeId = ds.getDataTypeId();

                    if (dataTypeId == null) {
                        continue;
                    }

                    List<String> subs = schemaToSubscriptions.getOrDefault(schemaId, List.of());

                    dataTypeToSubscriptions
                            .computeIfAbsent(dataTypeId, k -> new HashSet<>())
                            .addAll(subs);
                }

                return dataTypeToSubscriptions.entrySet().stream()
                        .map(e -> new DataTypeSubscriptionDTO(
                                e.getKey(),
                                e.getValue().size()
                        ))
                        .toList();
            });
        });
    }


    public Uni<List<Map<String, String>>> generateDummyData(DummyDataRequestDTO request) {
        if (request.getFeatures() == null || request.getFeatures().isEmpty()) {
            throw new BadRequestException("No data IDs provided");
        }
        int amount = request.getAmount();
        if (amount <= 0) {
            amount = 25;
        }

        List<UUID> dataTypeIds = new ArrayList<>();
        Map<UUID, String> dataTypeIdToFeatureName = new HashMap<>();

        for (PatientExportFeatureDTO feature : request.getFeatures()) {
            SelectedDataIdsDTO dataId = resolveSelectedDataId(feature);
            UUID dataTypeId;
            try {
                dataTypeId = UUID.fromString(dataId.getGlobalDataTypeId());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Invalid datatype UUID: " + dataId.getGlobalDataTypeId()
                );
            }

            dataTypeIds.add(dataTypeId);
            dataTypeIdToFeatureName.put(
                    dataTypeId,
                    feature.getName() != null && !feature.getName().isBlank()
                            ? feature.getName()
                            : dataId.getGlobalDataTypeId()
            );
        }

        int finalAmount = amount;
        return dataTypeDAO.getAllFiltered(dataTypeIds)
                .onItem().transform(el -> {
                    String rawDataId = dataTypeIdToFeatureName.get(el.getId());
                    List<String> values = randomDataGenerator.generateDummyData(el, finalAmount, 3);
                    return new AbstractMap.SimpleEntry<>(rawDataId, values);

                })
                .collect().asList()
                .onItem().transform(entries -> {
                    Map<String, List<String>> dataMap = new LinkedHashMap<>();
                    for (Map.Entry<String, List<String>> e : entries) {
                        dataMap.put(e.getKey(), e.getValue());
                    }
                    List<Map<String, String>> wideData = randomDataGenerator.createListOfDicts(dataMap);

                    if (request.isWideFormat()) {
                        return wideData;
                    }
                    return randomDataGenerator.wideFormatToLongFormat(wideData);
                });
    }

    private SelectedDataIdsDTO resolveSelectedDataId(PatientExportFeatureDTO feature) {
        if (feature == null || feature.getAllowedDataIds() == null || feature.getAllowedDataIds().isEmpty()) {
            throw new BadRequestException("No allowed data IDs provided");
        }

        return feature.getAllowedDataIds().stream()
                .filter(Objects::nonNull)
                .filter(dataId -> dataId.getGlobalDataTypeId() != null)
                .filter(dataId -> feature.getTargetDatatypeId() != null
                        && feature.getTargetDatatypeId().contains(dataId.getGlobalDataTypeId()))
                .findFirst()
                .orElse(feature.getAllowedDataIds().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("No allowed data IDs provided")));
    }


    public Uni<DataTypeNodeDTO> create(DataTypeNodeDTO dto) {
        return dataTypeDAO.create(dto)
                .flatMap(created ->
                        dataTypeDAO.updateEdges(dto)
                                .replaceWith(created)
                );
    }

    public Uni<DataTypeNodeDTO> getById(UUID id) {
        return dataTypeDAO.findById(id);
    }

    public Uni<ValidationResultDTO> checkValidations(UUID id, String value) {
        return dataTypeDAO.findById(id)
                .onItem()
                .transform(dataTypeNodeDTO -> {
                    ValidationResultDTO validationResultDTO = new ValidationResultDTO();
                    try {
                        validationBO.normalizeValidateValue(value, dataTypeNodeDTO);
                        return validationResultDTO;
                    } catch (IllegalArgumentException e) {
                        validationResultDTO.setValid(false);
                        validationResultDTO.setMessage(e.getMessage());
                        return validationResultDTO;
                    }
                });
    }

    public Uni<DataTypeNodeDTO> update(UUID id, DataTypeNodeDTO dto) {
        if (id == null || dto.getId() == null) {
            return Uni.createFrom().failure(
                    new BadRequestException("DataType id must not be null"));
        }
        if (!dto.getId().equals(id)) {
            return Uni.createFrom().failure(
                    new BadRequestException("DataType id in path and body must match"));
        }
        return dataTypeDAO.update(dto)
                .flatMap(updated ->
                        dataTypeDAO.updateEdges(dto)
                                .replaceWith(updated)
                );
    }

    public Uni<Void> delete(UUID id) {
        return dataTypeDAO.deleteById(id);
    }
}
