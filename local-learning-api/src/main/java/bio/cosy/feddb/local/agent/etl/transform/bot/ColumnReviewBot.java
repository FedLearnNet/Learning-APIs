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
            An agent selected the columns below to fill the schema node below. Judge whether they record its information:
            - Held: the columns record it, maybe coded, in other words, another language, unit or an equivalent
              classification system, or it follows from them by a fixed rule (e.g. a duration from two dates, an age in
              days from one in years).
            - Not held: the value could only be guessed from a related fact, e.g. one component of a combined grading
              from the combined value, a percentage from a score, a count or size from a free text, or the columns
              record another fact of the same topic.
            - When another column of the file records the value itself, the columns it would be computed from are not held.
            Answer held true, or held false and the reason, naming the column that records the information if one does.

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
    ColumnReview review(LocalSchemaNodeDTO schemaNode, String fileValues, List<String> columns);
}
