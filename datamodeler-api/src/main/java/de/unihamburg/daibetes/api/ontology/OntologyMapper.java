package de.unihamburg.daibetes.api.ontology;

import bio.cosy.feddb.core.api.datamodler.ontology.OntologyEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyQueryAbilityDTO;
import de.unihamburg.daibetes.base.BaseMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.util.Map;

@ApplicationScoped
public class OntologyMapper extends BaseMapper<OntologyNodeDTO, OntologyEdgeDTO> {

    @Override
    public OntologyNodeDTO fromNode(OntologyNodeDTO dto, Node node) {
        setHasSet(node, "names", dto::setNames);
        setString(node, "description", dto::setDescription);
        setHasSet(node, "codes", dto::setCodes);
        setHasSet(node, "sabs", dto::setSabs);
        setString(node, "cui", dto::setCui);
        setHasSet(node, "auis", dto::setAuis);
        setString(node, "lat", dto::setLat);
        return dto;
    }

    @Override
    public OntologyEdgeDTO fromEdge(OntologyEdgeDTO dto, Relationship edge) {
        setString(edge, "type", dto::setType);
        setString(edge, "description", dto::setDescription);
        setString(edge, "rel", dto::setRel);
        setString(edge, "rela", dto::setRela);
        setString(edge, "sab", dto::setSab);
        return dto;
    }

    @Override
    public Map<String, Object> toNodeProperties(Map<String, Object> map, OntologyNodeDTO dto) {
        setSet(map, "names", dto.getNames());
        setValue(map, "description", dto.getDescription());
        setSet(map, "codes", dto.getCodes());
        setSet(map, "sabs", dto.getSabs());
        setValue(map, "cui", dto.getCui());
        setSet(map, "auis", dto.getAuis());
        setValue(map, "lat", dto.getLat());
        return map;
    }

    @Override
    public Map<String, Object> toEdgeProperties(Map<String, Object> map, OntologyEdgeDTO dto) {
        setValue(map, "type", dto.getType());
        setValue(map, "description", dto.getDescription());
        setValue(map, "rel", dto.getRel());
        setValue(map, "rela", dto.getRela());
        setValue(map, "sab", dto.getSab());
        return map;
    }


    public OntologyQueryAbilityDTO toQueryAbility(Record record) {
        OntologyQueryAbilityDTO dto = new OntologyQueryAbilityDTO();
        setString(record, "ontologyId", dto::setOntologyId);
        setString(record, "dataTypeId", dto::setDataTypeId);
        setList(record, "clients", dto::setClients);
        return dto;
    }

    @Override
    public OntologyNodeDTO newNode() {
        return new OntologyNodeDTO();
    }

    @Override
    public OntologyEdgeDTO newEdge() {
        return new OntologyEdgeDTO();
    }
}
