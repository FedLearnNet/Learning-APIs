package de.unihamburg.daibetes.api.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import de.unihamburg.daibetes.api.ontology.OntologyMapper;
import de.unihamburg.daibetes.base.BaseMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class DataTypeMapper extends BaseMapper<DataTypeNodeDTO, DataTypeEdgeDTO> {

    @Inject
    ObjectMapper mapper;

    @Inject
    OntologyMapper ontologyMapper;

    public DataTypeNodeDetailDTO fromDetailRecord(Record record) {
        DataTypeNodeDetailDTO dto = new DataTypeNodeDetailDTO();
        if (record.containsKey("node") && !record.get("node").isNull()) {
            Node node = record.get("node").asNode();
            setId(node, idPropertyName, dto::setId);
            setString(node, "label", dto::setLabel);
            fromNode(dto, node);
        }

        if (record.containsKey("schemaIds") && !record.get("schemaIds").isNull()) {
            dto.setSchemaIds(
                    record.get("schemaIds").asList(Value::asString)
            );
        }

        if (record.containsKey("ontologies") && !record.get("ontologies").isNull()) {
            List<OntologyNodeDTO> ontologyDTOs = record.get("ontologies")
                    .asList(v -> {
                        Node oNode = v.asNode();
                        return ontologyMapper.fromNode(oNode);
                    });
            dto.setOntologies(ontologyDTOs);
            dto.setOntologyIds(
                    ontologyDTOs.stream().map(OntologyNodeDTO::getId).map(Object::toString).toList()
            );
        }

        return dto;
    }

    @Override
    public DataTypeNodeDTO fromNode(DataTypeNodeDTO dto, Node node) {
        setString(node, "name", dto::setName);
        setString(node, "description", dto::setDescription);
        setString(node, "type", dto::setType);
        setBoolean(node, "allowNullValues", dto::setAllowNullValues);
        setBoolean(node, "isRequired", dto::setIsRequired);
        setList(node, "allowedValues", dto::setOptions);

        if (node.containsKey("validations")) {
            String validationsStr = node.get("validations").asString();
            dto.setValidations(stringToValidations(validationsStr));
        }
        setList(node, "ontologyIds", dto::setOntologyIds);
        setList(node, "schemaIds", dto::setSchemaIds);

        return dto;
    }

    @Override
    public DataTypeEdgeDTO fromEdge(DataTypeEdgeDTO dto, Relationship edge) {
        return dto;
    }

    @Override
    public Map<String, Object> toNodeProperties(Map<String, Object> map, DataTypeNodeDTO dto) {
        setValue(map, "description", dto.getDescription());
        setValue(map, "name", dto.getName());
        setValue(map, "type", dto.getType().toString());
        setValue(map, "allowedValues", dto.getOptions());
        setValue(map, "validations", validationsToString(dto.getValidations()));
        setValue(map, "ontologyIds", dto.getOntologyIds());
        setValue(map, "schemaIds", dto.getSchemaIds());
        setValue(map, "isRequired", dto.getIsRequired());
        setValue(map, "allowNullValues", dto.getAllowNullValues());
        return map;
    }

    @Override
    public Map<String, Object> toEdgeProperties(Map<String, Object> map, DataTypeEdgeDTO dto) {
        return map;
    }


    private String validationsToString(List<DataTypeValidationDTO> validations) {
        try {
            List<DataTypeValidationDTO> filteredValidations = validations == null
                    ? List.of()
                    : validations.stream()
                      .filter(validation -> validation.getName() != null)
                      .toList();

            return mapper.writeValueAsString(filteredValidations);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing validation", e);
        }
    }

    private List<DataTypeValidationDTO> stringToValidations(String validation) {
        try {
            ObjectReader reader = mapper.copy()
                    .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                    .readerFor(mapper.getTypeFactory()
                            .constructCollectionType(List.class, DataTypeValidationDTO.class));

            return reader.<List<DataTypeValidationDTO>>readValue(validation)
                    .stream()
                    .filter(validationDTO -> validationDTO.getName() != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Error deserializing validation", e);
        }
    }

    @Override
    public DataTypeNodeDTO newNode() {
        return new DataTypeNodeDTO();
    }

    @Override
    public DataTypeEdgeDTO newEdge() {
        return new DataTypeEdgeDTO();
    }
}
