package eval.agent.etl.gdc.judge;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/** Judges a mapping the ground truth does not list, as the ground truths of GDC-SM and OMAP name only some right ones. */
@RegisterAiService(modelName = "judge")
public interface GdcJudge {

    @UserMessage("""
            An ETL agent filled the {schema} field below from columns of a study table, but the ground truth of the
            benchmark does not list this mapping. Judge whether it is still right: the columns record the information
            of the field, maybe coded, in other words or units, or it follows from them by a fixed rule. It is wrong
            when the value is only guessed from a related fact (e.g. one component of a combined stage from the whole,
            a percentage from a score) or the columns record another fact of the same topic.
            Keep the reason to one plain sentence.

            {schema} field: {field}
            Columns with example values: {columns}
            Transformations: {configuration}
            """)
    GdcJudgement judge(String schema, String field, String columns, String configuration);
}
