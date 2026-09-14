package de.unihamburg.daibetes.agent.anlysis.tools;

import de.unihamburg.daibetes.api.analysis.llm.DataAnalysisLLMBO;
import de.unihamburg.daibetes.api.analysis.llm.ResultAnalyzerResultDTO;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ResultAnalyzerTools {

    @Inject
    DataAnalysisLLMBO dataAnalysisLLMBO;

    public String analyzeFileById(Long workflowId, Long fileId, String keycloakId, String subquestion) {
        return Uni.createFrom().item(() -> {
                    return dataAnalysisLLMBO.getAnalysableDataTransaction(workflowId, fileId, keycloakId);
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(file -> dataAnalysisLLMBO.startLLMDataAnalysisWorkflow(file, subquestion))
                .collect().asList()
                .map(list -> list.stream().map(ResultAnalyzerResultDTO::getResult).toList())
                .map(list -> String.join("", list))
                .await().indefinitely();
    }
}
