package bio.cosy.feddb.local.agent.etl.transform.bot;

import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface ColumnReviewBot {

    @UserMessage("""
            An agent selected the columns below to fill the schema node below. Judge whether they record its information,
            by their values rather than their names:
            - Held: the values are the information, maybe coded, in other words, another language, unit, form or
              reference point, or an equivalent classification system (e.g. race terms in a column named ethnicity),
              or it follows from them by a fixed rule (e.g. a duration from two dates, days to birth from an age).
              Codes, symbols or abbreviations other than the allowed values are converted later, so judge whether the
              column is about the node, not whether its values are allowed already; when the description lists the
              share of each value, codes with about the same shares are its values, whatever the column is named.
              A cell holding several values joined by a separator holds each of them, they are split later and keep
              their order (e.g. the first code for the first of numbered nodes); a column with one code in all rows
              holds the absent value. When no column records the node more exactly, a
              neighbouring fact recorded directly holds it as well (e.g. the country of origin for the country of
              residence).
            - Not held: the value could only be guessed from a related fact, e.g. a component the values do not write
              out (a T category from an overall stage), a percentage from a score, a count or size from a free text.
              Neither is a node specific to another disease, body site or kind of sample than the file is about, or a
              node describing the record itself rather than the patient (e.g. files, links, sample handling).
            - When another column of the file records the value itself, the columns it would be computed from are not held.
            {#if filledNodes}
            - The columns already fill these schema nodes: {filledNodes}. A column records few facts, so it fills this
              node as well only when the node asks for the same fact under another name, unit or form (e.g. an age as
              days to birth), or the column holds several values that are split later. A node about another subject,
              time point or quantity, or a detail the column does not record, is not held.
            {/if}
            Answer held true, or held false and the reason, naming the column of the list below that records the
            information if one does.

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type}){/if}

            Selected columns with their values in the file:
            {fileValues}

            All columns of the file with example values:
            {#for column in columns}
            - {column}
            {/for}
            """)
    ColumnReview review(LocalSchemaNodeDTO schemaNode, String fileValues, List<String> columns, String filledNodes);
}
