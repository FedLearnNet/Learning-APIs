package bio.cosy.feddb.local.api.importer.transformer.code;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CodeTransformerAO implements PanacheRepository<CodeTransformerEntity> {
}
