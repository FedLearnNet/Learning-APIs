package bio.cosy.feddb.local.api.file;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FileAO implements PanacheRepository<FileEntity> {

    public List<FileEntity> getAll(String keycloakId) {
        return list("keycloakId = ?1", keycloakId);
    }
}
