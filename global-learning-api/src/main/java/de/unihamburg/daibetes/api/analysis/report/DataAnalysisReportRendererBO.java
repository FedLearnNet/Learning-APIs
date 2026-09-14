package de.unihamburg.daibetes.api.analysis.report;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import io.quarkus.logging.Log;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;

@ApplicationScoped
public class DataAnalysisReportRendererBO {
    @Inject
    Template dataAnalysis;

    @Inject
    Template llmAnalysisReport;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    DataAnalysisReportMapper reportMapper;

    public String getSummaryForAgents(Long id, String keycloakId) {
        DataAnalysisDetailDTO analysis = dataAnalysisBO.findById(id, keycloakId);
        if (analysis == null) {
            return "DataAnalysis with id " + id + " not found";
        }
        ReportView view = reportMapper.runToReport(analysis);
        return llmAnalysisReport
                .data("v", view)
                .render();
    }


    public byte[] render(Long id, String keycloakId) {
        DataAnalysisDetailDTO analysis = dataAnalysisBO.findByIdFlat(id, keycloakId);
        ReportView view = reportMapper.runToReport(analysis);
        String html = dataAnalysis
                .data("v", view)
                .render();
        html = sanitizeForXhtml(html);
        if (Log.isDebugEnabled()) {
            Log.debugf("Report HTML head: %s", previewHead(html, 120));
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(128_000)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render DataAnalysis report PDF", e);
        }
    }

    private static String sanitizeForXhtml(String html) {
        if (html == null) return "";

        // strip UTF-8 BOM if present
        if (!html.isEmpty() && html.charAt(0) == '\uFEFF') {
            html = html.substring(1);
        }

        // remove leading junk/whitespace before first '<'
        int firstLt = html.indexOf('<');
        if (firstLt > 0) {
            html = html.substring(firstLt);
        }

        return html.trim();
    }

    private static String previewHead(String s, int n) {
        if (s == null) return "null";
        int len = Math.min(n, s.length());
        return s.substring(0, len)
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
