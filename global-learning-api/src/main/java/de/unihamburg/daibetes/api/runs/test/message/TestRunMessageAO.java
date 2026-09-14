package de.unihamburg.daibetes.api.runs.test.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TestRunMessageAO implements PanacheRepository<TestRunMessageEntity> {

    public List<TestRunMessageEntity> findByRun(Long id) {
        return list("run.id", id);
    }

    public List<TestRunMessageEntity> findByRun(Long id, RunMessageTypes type) {
        return list("run.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

}
