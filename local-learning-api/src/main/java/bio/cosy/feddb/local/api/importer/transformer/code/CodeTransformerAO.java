package bio.cosy.feddb.local.api.importer.transformer.code;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class CodeTransformerAO implements PanacheRepository<CodeTransformerEntity> {

    public CodeTransformerEntity findByIdChecked(Long id) {
        if (id == null) {
            throw new NotFoundException("id is null, can not found Custom transformer");
        }
        return findByIdOptional(id).orElseThrow(() -> new NotFoundException("Custom transformer " + id + " not found"));
    }
}
