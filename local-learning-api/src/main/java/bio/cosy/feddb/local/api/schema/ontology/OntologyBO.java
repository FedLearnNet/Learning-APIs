package bio.cosy.feddb.local.api.schema.ontology;

import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class OntologyBO extends BaseBo<OntologyDTO, OntologyEntity, OntologyAO, OntologyMapper> {


    public OntologyEntity fetchOrCreate(OntologyDTO dto) {

        Optional<OntologyEntity> optionalExistingOntology = ao.findByGlobalId(dto.getGlobalId());
        if (optionalExistingOntology.isPresent()) {
            // Ontology exists, ensure it matches the global schema node
            OntologyEntity existingOntology = optionalExistingOntology.get();
            if (!isEqual(dto, existingOntology)) {
                throw new IllegalStateException(
                        "Local ontology does not match global ontology. Local: " + existingOntology +
                                ", Global: " + dto);
            }
            return existingOntology;
        } else {
            // Ontology does not exist but the schema needs it, create the ontology
            OntologyEntity localOntology = mapper.dtoToEntity(dto);
            ao.persist(localOntology);
            return localOntology;
        }
    }

    private boolean isEqual(OntologyDTO dto, OntologyEntity entity) {
        return dto.getGlobalId().equals(entity.getGlobalId()) &&
                dto.getVersion().equals(entity.getVersion());
    }

    public void delete(Set<OntologyEntity> entities) {
        for (OntologyEntity ontology : entities) {
            try {
                // Check if this ontology is still referenced by any remaining schema nodes
                if (ontology.getSchemaNodes() == null || ontology.getSchemaNodes().isEmpty()) {
                    // Ontology is not used by any schema node anymore, so we can remove it
                    ao.delete(ontology);
                }
            } catch (jakarta.persistence.EntityNotFoundException e) {
                // Ontology was already deleted, which is fine
            }
        }
    }
}
