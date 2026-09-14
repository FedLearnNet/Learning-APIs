package bio.cosy.feddb.core.agent.util;

import bio.cosy.feddb.core.agent.pojo.LLMAgentFileFixture;
import bio.cosy.feddb.core.agent.pojo.LocalTestFileUpload;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TestFileUploader {


    @Inject
    DataAnalysisFileBO dataAnalysisFileBO;

    @Transactional
    public List<DataAnalysisFileDTO> uploadRequiredFiles(Long workflowId,
                                                         List<LLMAgentFileFixture> requiredFiles,
                                                         String keycloakId) {
        if (requiredFiles == null || requiredFiles.isEmpty()) {
            return List.of();
        }

        List<DataAnalysisFileDTO> uploaded = new ArrayList<>();
        for (LLMAgentFileFixture fixture : requiredFiles) {
            uploaded.add(uploadFixture(workflowId, fixture, keycloakId));
        }
        return uploaded;
    }

    public DataAnalysisFileDTO uploadFixture(Long workflowId,
                                             LLMAgentFileFixture fixture,
                                             String keycloakId) {
        try {
            Path tempFile = materializeFixture(fixture);
            FileUpload upload = new LocalTestFileUpload(
                    "file",
                    fixture.getDisplayName(),
                    tempFile,
                    "text/csv"
            );
            return dataAnalysisFileBO.storeFile(workflowId, upload, keycloakId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload fixture: " + fixture.getResourcePath(), e);
        }
    }

    private Path materializeFixture(LLMAgentFileFixture fixture) {
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(fixture.getResourcePath())) {

            if (in == null) {
                throw new IllegalArgumentException("Fixture resource not found: " + fixture.getResourcePath());
            }

            String suffix = fixture.getDisplayName() != null && fixture.getDisplayName().contains(".")
                    ? fixture.getDisplayName().substring(fixture.getDisplayName().lastIndexOf('.'))
                    : ".tmp";

            Path tmp = Files.createTempFile("posymed-eval-", suffix);
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            throw new IllegalStateException("Could not materialize fixture: " + fixture.getResourcePath(), e);
        }
    }
}
