package eval.agent.etl.transform.judge;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "judge")
public interface TransformerWriterJudge {

    @SystemMessage("""
            You review one mapping of an ETL harmonization agent: how the columns of a local clinical export are
            mapped onto one node of a shared schema, and how their values are transformed on the way. You get the
            schema node, the profiles of the source columns, the transformations (custom Python code included) and
            what the import of the whole dataset with the agent's connector stored for the schema node.
            Give two independent verdicts:
            - mappingCorrect: the source columns hold the information the schema node asks for: the right concept,
              every column the value depends on and none that only looks similar. If the export has several rows
              per patient, the visit id column has to identify the visit of a row and the timestamp column the time
              of the value; a wrong visit column makes the mapping incorrect.
            - transformationCorrect: every source value is converted into the semantically right value of the schema
              node: the right allowed value for a code or term, the right unit and arithmetic, the right range. Judge
              the meaning, not the code style. Compare the source values with the imported ones and read the code. A
              missing or unknown source value has to become empty, unless the schema has a value for it (e.g. 'None'
              meaning not measured).
            - If the agent found no mapping, both are true only if none of the columns of the export holds the
              information; otherwise both are false and you name the column.
            - A value that is valid for the data type can still be wrong; a valid looking output is no proof.
            - Rejected values, a suspicious distribution (e.g. everything empty or one value only) or a failed run
              point to errors.
            Explain the verdicts in the reason, naming the wrong conversions if there are any. Keep the reason to
            at most three plain sentences, without Markdown, the answer is cut off otherwise.
            """)
    @UserMessage("""
            Judge this mapping:

            {review}
            """)
    TransformerWriterVerdict judge(String review);
}
