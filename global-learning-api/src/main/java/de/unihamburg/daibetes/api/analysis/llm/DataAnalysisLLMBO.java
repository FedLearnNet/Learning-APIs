package de.unihamburg.daibetes.api.analysis.llm;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import de.unihamburg.daibetes.agent.anlysis.bots.ResultAnalyzer;
import de.unihamburg.daibetes.agent.anlysis.pojo.FileResultAnalyzer;
import de.unihamburg.daibetes.agent.anlysis.pojo.ResultAnalyzerHyperParam;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepBO;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.file.FileBO;
import dev.langchain4j.data.image.Image;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DataAnalysisLLMBO {

    @Inject
    DataAnalysisBO bo;

    @Inject
    ResultAnalyzer resultAnalyzer;

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    DataAnalysisPredictionBO dataAnalysisPredictionBO;

    @Inject
    DataAnalysisWorkflowRunStepBO dataAnalysisWorkflowRunStepBO;

    @Inject
    FederatedAppBO appBO;

    @Inject
    FileBO fileBO;

    @Transactional
    public FileResultAnalyzer getAnalysableDataTransaction(Long id, Long fileId, String keycloakId) {
        DataAnalysisFileEntity fileEntity = modelWorkflowFileBO.getFileEntity(id, fileId, keycloakId);
        FileContentDTO fileContent = modelWorkflowFileBO.getFileContent(fileEntity);

        DataAnalysisPredictionDTO prediction = null;
        if (fileEntity.getPrediction() != null) {
            prediction = dataAnalysisPredictionBO.getById(fileEntity.getPrediction().getId());
        }
        if (fileEntity.getWorkflowStep() != null) {
            prediction = dataAnalysisWorkflowRunStepBO.getResult(fileEntity.getWorkflowStep().getId());
        }
        if (prediction == null || prediction.getAppVersionId() == null) {
            Log.infof("No associated app found for file %d in data analysis %d, performing raw file analysis", fileId, id);
            FileResultAnalyzer fileResultAnalyzer = new FileResultAnalyzer();
            fileResultAnalyzer.setToolName(null); //will use subquestion if tool name is null
            fileResultAnalyzer.setFileType(fileEntity.getFile().getContentType());
            fileResultAnalyzer.setFileContent(fileContent);
            fileResultAnalyzer.setFileProfile(fileBO.getFileStatistics(fileEntity.getFile()));
            return fileResultAnalyzer;
        }
        FederatedAppDetailDTO app = appBO.getByVersionId(prediction.getAppVersionId());
        if (app == null) {
            throw new NotFoundException("No valid app found for the prediction's app version ID");
        }
        FileResultAnalyzer fileResultAnalyzer = new FileResultAnalyzer();
        fileResultAnalyzer.setToolName(app.getName());
        fileResultAnalyzer.setToolDescription(app.getLongDescription());
        fileResultAnalyzer.setHyperParams(getHyperParams(app, prediction.getHyperParams()));
        fileResultAnalyzer.setInputs(getInputs(app, prediction.getInputs(), prediction.getInputFiles()));
        fileResultAnalyzer.setFileType(fileEntity.getFile().getContentType());
        fileResultAnalyzer.setFileContent(fileContent);
        fileResultAnalyzer.setFileProfile(fileBO.getFileStatistics(fileEntity.getFile()));
        return fileResultAnalyzer;
    }

    public Multi<ResultAnalyzerResultDTO> startLLMDataAnalysisWorkflow(FileResultAnalyzer fileResultAnalyzer, String subQuestion) {
        if (fileResultAnalyzer == null || fileResultAnalyzer.getFileContent() == null) {
            throw new NotFoundException("file not found");
        }
        if (fileResultAnalyzer.getFileContent().getType().equals(ToolConfigDataType.IMAGE)) {
            Image img = Image.builder()
                    .base64Data(fileResultAnalyzer.getFileContent().getContent())
                    .mimeType(fileResultAnalyzer.getFileType())
                    .build();
            if (StringUtils.isEmpty(fileResultAnalyzer.getToolName())) {
                Multi<String> analysisResult = resultAnalyzer.analyzeFile(subQuestion, img);
                return analysisResult
                        .onItem().transform(ResultAnalyzerResultDTO::new);
            }
            Multi<String> analysisResult = resultAnalyzer.analyzeFile(fileResultAnalyzer, img);
            return analysisResult
                    .onItem().transform(ResultAnalyzerResultDTO::new);
        } else {
            if (StringUtils.isEmpty(fileResultAnalyzer.getToolName())) {
                Multi<String> analysisResult = resultAnalyzer.analyzeTextFile(fileResultAnalyzer.getFileProfile(), subQuestion);
                return analysisResult
                        .onItem().transform(ResultAnalyzerResultDTO::new);
            }
            Multi<String> analysisResult = resultAnalyzer.analyzeTextFile(fileResultAnalyzer, fileResultAnalyzer.getFileProfile());
            return analysisResult
                    .onItem().transform(ResultAnalyzerResultDTO::new);
        }
    }

    private List<ResultAnalyzerHyperParam> getHyperParams(FederatedAppDetailDTO app, LinkedHashMap<String, Object> values) {
        return app.getAppConfig().getHyperparams().stream().map(h -> {
            String value = null;
            if (values.containsKey(h.getName())) {
                value = values.get(h.getName()).toString();
            }
            if (values.containsKey(h.getVariableName())) {
                value = values.get(h.getVariableName()).toString();
            }
            return new ResultAnalyzerHyperParam(h.getName(), value, h.getDescription());
        }).toList();

    }

    private List<ResultAnalyzerHyperParam> getInputs(FederatedAppDetailDTO app,
                                                     LinkedHashMap<String, Object> values,
                                                     List<DataAnalysisFileDTO> inputFiles) {
        return app.getAppConfig().getHyperparams().stream().map(h -> {
            String value = null;
            if (values.containsKey(h.getName())) {
                value = values.get(h.getName()).toString();
            }
            if (values.containsKey(h.getVariableName())) {
                value = values.get(h.getVariableName()).toString();
            }
            if (value == null) {
                DataAnalysisFileDTO file = Optional.ofNullable(inputFiles)
                        .map(fs -> fs.stream()
                                .findFirst().orElseGet(() -> null))
                        .orElseGet(() -> null);
                if (file == null) {
                    return new ResultAnalyzerHyperParam(h.getName(), null, h.getDescription());
                }
                value = modelWorkflowFileBO.getFileContentForAnalyzing(file.getDataAnalysisId(), file.getFile().getId(), file.getKeycloakId());
            }
            return new ResultAnalyzerHyperParam(h.getName(), value, h.getDescription());
        }).toList();

    }
}
