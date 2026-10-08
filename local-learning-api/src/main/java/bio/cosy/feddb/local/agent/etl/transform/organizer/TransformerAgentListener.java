package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerSelectAgent;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWriterAgent;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

// Quarkus adds every AgentListener bean to the agents; this one tells the user tool which workflow agent asks
@ApplicationScoped
public class TransformerAgentListener implements AgentListener {

    private static final Set<String> WORKFLOW_AGENTS = Set.of(TransformerSelectAgent.NAME, TransformerWriterAgent.NAME);

    @Inject
    TransformerUserTool userTool;

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (WORKFLOW_AGENTS.contains(request.agentName())) {
            userTool.askedBy(request.agentName());
        }
    }
}
