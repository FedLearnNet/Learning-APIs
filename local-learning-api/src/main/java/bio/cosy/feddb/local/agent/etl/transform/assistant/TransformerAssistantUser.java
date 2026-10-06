package bio.cosy.feddb.local.agent.etl.transform.assistant;

import java.util.List;

public interface TransformerAssistantUser {

    String ask(String question);

    List<String> selectColumns(String question, List<String> columns, boolean multiple);
}
