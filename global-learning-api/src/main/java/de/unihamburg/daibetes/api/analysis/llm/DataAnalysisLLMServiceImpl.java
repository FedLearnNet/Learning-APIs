package de.unihamburg.daibetes.api.analysis.llm;

import de.unihamburg.daibetes.agent.anlysis.pojo.FileResultAnalyzer;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DataAnalysisLLMServiceImpl implements DataAnalysisLLMService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    DataAnalysisLLMBO dataAnalysisLLMBO;

    @Override
    public Multi<ResultAnalyzerResultDTO> analysisResult(Long id, Long fileId) {
        return Uni.createFrom().item(() -> {
                    String keycloakId = userIdentity.getKeycloakId();
                    return dataAnalysisLLMBO.getAnalysableDataTransaction(id, fileId, keycloakId);
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(file -> dataAnalysisLLMBO.startLLMDataAnalysisWorkflow(file, ""));
    }
}
