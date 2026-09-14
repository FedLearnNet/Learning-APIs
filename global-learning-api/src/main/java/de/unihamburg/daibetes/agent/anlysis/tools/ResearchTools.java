package de.unihamburg.daibetes.agent.anlysis.tools;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageBO;
import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarServiceImpl;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.quarkus.arc.ComponentsProvider.LOG;

@ApplicationScoped
public class ResearchTools {

    @Inject
    DataAnalysisChatMessageBO messageBO;

    @Inject
    SemanticScholarServiceImpl semanticScholarService;

    @Tool("Useful for searching academic papers and articles on Semantic Scholar. "
            + "Provide a concise summary of the most relevant papers including title, authors, year, abstract, URL, and DOI. "
            + "If no results are found, respond with 'No results found.'")
    public String querySemanticScholar(@P(value = "query") String query, @ToolMemoryId String sessionId) {
        ToolDTO toolDTO = new ToolDTO("RESEARCH");
        toolDTO.setInput(query);

        String result;
        try {
            var response = semanticScholarService.executeQuery(query);
            if (response != null && response.getData() != null) {
                StringBuilder sb = new StringBuilder();
                for (var paper : response.getData()) {
                    sb.append("Paper ID: ").append(paper.getPaperId()).append("\n");
                    sb.append("Title: ").append(paper.getTitle()).append("\n");
                    if (paper.getYear() != null) {
                        sb.append("Year: ").append(paper.getYear()).append("\n");
                    }
                    if (paper.getAbstractText() != null) {
                        sb.append("Abstract: ").append(paper.getAbstractText()).append("\n");
                    }
                    sb.append("-----\n");
                }
                result = sb.toString();
            } else {
                result = "No results found.";
            }
        } catch (Exception e) {
            LOG.error("Error querying Semantic Scholar", e);
            return "Error querying Semantic Scholar: " + e.getMessage();
        }
        toolDTO.addContent(result);
        toolDTO.setStop();
        messageBO.sendTool(toolDTO, sessionId);
        return result;
    }
}

