package bio.cosy.feddb.local.api.importer.files;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorFilesAO implements PanacheRepository<ConnectorFilesEntity> {

    public List<ConnectorFilesEntity> getFilesByCohort(Long cohortId) {
        return list("cohort.id", cohortId);
    }

    public boolean existById(Long id) {
        return count("id", id) > 0;
    }


    public long deleteFileByCohortId(Long cohortId, Long fileId) {
        return delete("cohort.id = ?1 and id = ?2", cohortId, fileId);
    }

    public Optional<ConnectorFilesEntity> getFileByCohortId(Long cohortId, Long fileId) {
        return find("cohort.id = ?1 and id = ?2", cohortId, fileId).firstResultOptional();
    }

    public Optional<ConnectorFilesEntity> getFirstFileByCohortId(Long cohortId) {
        return find("cohort.id = ?1 ORDER BY id DESC", cohortId).firstResultOptional();
    }
}
