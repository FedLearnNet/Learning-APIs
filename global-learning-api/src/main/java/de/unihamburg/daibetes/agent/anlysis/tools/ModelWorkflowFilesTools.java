package de.unihamburg.daibetes.agent.anlysis.tools;

import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ModelWorkflowFilesTools {

    @Inject
    DataAnalysisFileBO fileBo;

    private static final String FILE_DETAIL = """
            # File
            **Name:** %s
            **ID:** %s
            **Type:** %s
            **Size:** %s
            """;


    @Tool("List available files files that the user can ask about")
    @Transactional
    public List<String> listFiles() {
        return fileBo.getAll().stream().map(this::mapFile)
                .toList();
    }

    @Tool("Analyze a specific CSV file by name. Provide an executive summary: inferred schema, missingness, basic statistics, and notable category distributions.")
    @Transactional
    public FileProfile summarizeFile(String fileName) {
        return fileBo.getFileStatisticsByName(fileName);
    }

    @Tool("Return the first 10 lines of a file by name. Useful for quick inspection of the data.")
    @Transactional
    public String getFirst10Lines(String fileName) {
        FileContentDTO file = fileBo.getFileContentByName(fileName);
        return file.getContent().substring(0, 1000);
    }

    @Transactional
    public List<String> listFiles(Long workflowId, String keycloakId) {
        return fileBo.listAll(workflowId, keycloakId, false).stream().map(this::mapFile)
                .toList();
    }

    @Transactional
    public List<String> listFiles(String keycloakId) {
        return fileBo.listAll(keycloakId, false).stream().map(this::mapFile)
                .toList();
    }

    private String mapFile(DataAnalysisFileDTO file) {
        return String.format(FILE_DETAIL, file.getFile().getFileName(), file.getId(), file.getFile().getContentType(),
                file.getFile().getSize() + " bytes");
    }


}
