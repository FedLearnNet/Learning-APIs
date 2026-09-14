package de.unihamburg.daibetes.api.analysis.chat;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;


@ApplicationScoped
public class DataAnalysisChatMessageAO implements PanacheRepository<DataAnalysisChatMessageEntity> {

    public List<DataAnalysisChatMessageEntity> findByDataAnalysis(Long id, String keycloakId) {
        return list("dataAnalysis.id = ?1 AND dataAnalysis.keycloakId = ?2",
                Sort.by("id", Sort.Direction.Ascending),
                id, keycloakId);
    }

}
