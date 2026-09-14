package de.unihamburg.daibetes.api.runs.test;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import de.unihamburg.daibetes.api.runs.test.message.TestRunMessageEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TestRunAO implements PanacheRepository<TestRunEntity> {

    public List<TestRunEntity> findByAppId(Long id) {
        return list("federatedAppVersion.federatedApp.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);

    }

    public List<TestRunEntity> findAllRunningByAppId(Long id) {
        return list("federatedAppVersion.federatedApp.id = ?1 and status not in ?2", id, List.of(RunStatusTypes.FINISHED,
                RunStatusTypes.ERROR));

    }

}
