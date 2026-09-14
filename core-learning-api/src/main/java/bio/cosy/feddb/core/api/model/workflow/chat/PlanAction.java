package bio.cosy.feddb.core.api.model.workflow.chat;

public enum PlanAction {
    SUMMARIZE_PREVIOUS_WORK,
    FETCH_TOOLS,
    FETCH_DATA,
    PLAN_NEXT_STEP,
    ANALYZE_DATA,
    RESEARCH_PAPERS,
    EXTERNAL_AGENT,
    HUMAN_IN_THE_LOOP,
    FINALIZE
}
