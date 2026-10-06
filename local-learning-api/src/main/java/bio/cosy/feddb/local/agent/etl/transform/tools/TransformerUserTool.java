package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.RequestScoped;

import java.util.List;

@RequestScoped
public class TransformerUserTool {

    private TransformerAssistantUser user;
    private List<String> columns;

    public void init(TransformerAssistantUser user, List<String> columns) {
        this.user = user;
        this.columns = columns;
    }

    @Tool("Ask the user a question and wait for the answer, e.g. when several functions fit equally well")
    public String askUser(@P("The question, including the options to choose from") String question) {
        return user.ask(question);
    }

    @Tool("Let the user select columns of the data and wait for the selection, e.g. when several columns could hold the information")
    public List<String> selectColumns(@P("The question, e.g. which columns hold the diagnoses") String question,
                                      @P("Whether the user may select more than one column") boolean multiple) {
        return user.selectColumns(question, columns, multiple);
    }
}
