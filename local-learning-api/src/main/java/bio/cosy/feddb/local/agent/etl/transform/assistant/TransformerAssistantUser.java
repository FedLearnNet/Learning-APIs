package bio.cosy.feddb.local.agent.etl.transform.assistant;

import java.util.List;

public interface TransformerAssistantUser {

    String ask(String agent, String question);

    List<String> selectColumns(String agent, String question, List<String> columns, boolean multiple);
}
