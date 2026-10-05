package bio.cosy.feddb.local.agent.etl.transform.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.RequestScoped;

import java.util.function.UnaryOperator;

@RequestScoped
public class TransformerUserTool {

    private UnaryOperator<String> askUser;

    public void init(UnaryOperator<String> askUser) {
        this.askUser = askUser;
    }

    @Tool("Ask the user a question and wait for the answer, e.g. when several columns or functions fit equally well")
    public String askUser(@P("The question, including the options to choose from") String question) {
        return askUser.apply(question);
    }
}
