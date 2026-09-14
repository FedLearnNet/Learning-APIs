package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

@ApplicationScoped
public class DataAnalysisResultSender {
    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<DataAnalysisPredictionDTO> infoEmitter;

    @Inject
    DataAnalysisWorkflowResultMapper workflowResultMapper;


    public void sendMessage(DataAnalysisResultDTO info) {
        try {
            infoEmitter.send(info);
            Log.debug("SSE event sent: " + info);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }


    public void sendMessage(DataAnalysisWorkflowRunDTO info) {
// implement if needed
    }

    public void sendMessage(DataAnalysisPredictionDTO info) {
        DataAnalysisResultDTO result = workflowResultMapper.predictionToResult(info);
        sendMessage(result);
    }
}
