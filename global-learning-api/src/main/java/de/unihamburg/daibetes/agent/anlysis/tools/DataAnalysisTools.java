package de.unihamburg.daibetes.agent.anlysis.tools;

import de.unihamburg.daibetes.api.analysis.report.DataAnalysisReportRendererBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataAnalysisTools {
    @Inject
    DataAnalysisReportRendererBO dataAnalysisReportRendererBO;

    @Transactional
    public String getAnalysisReportTransactional(Long id, String keycloakId) {
        return dataAnalysisReportRendererBO.getSummaryForAgents(id, keycloakId);
    }
}
