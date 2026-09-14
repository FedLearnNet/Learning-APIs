package de.unihamburg.daibetes.agent.anlysis;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import de.unihamburg.daibetes.agent.anlysis.tools.PlanNextStepDecision;
import de.unihamburg.daibetes.services.research.semanticscholar.SemanticScholarPaperDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PlanState {
    private String userGoal;
    private String previousContext;

    private Long workflowId;

    private String dataAnalysisReport;
    private String dataAnalysisSummary;
    private List<String> tools = new ArrayList<>();
    private String papers;
    private List<String> dataProfiles = new ArrayList<>();
    private Map<Long, String> fileAnalyzeMap = new HashMap<>();
    private List<HumanInTheLoopDTO> humanInTheLoop;

    private PlanNextStepDecision nextStepDecision;
    private StoreDTO nextStoreItem;

    private String feedback;

    public void addFileAnalyzeResult(Long fileId, String result) {
        this.fileAnalyzeMap.put(fileId, result);
    }

    public void addTool(String tool) {
        this.tools.add(tool);
    }

    public void addHumanInTheLoop(String request, String answer) {
        if (this.humanInTheLoop == null) {
            this.humanInTheLoop = new ArrayList<>();
        }
        this.humanInTheLoop.add(new HumanInTheLoopDTO(request, answer));
    }
}
