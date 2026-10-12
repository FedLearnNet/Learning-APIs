package bio.cosy.feddb.local.agent.etl.transform.bot;

import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface NotFoundReviewBot {

    @UserMessage("""
            An agent had to map the selected columns onto the schema node below and answered that no existing
            function computes it, with the reason below. Classify the reason:
            - ASKS_USER: it asks the user something or waits for the user's input. The agent has a tool to ask the
              user and has to use it instead.
            - BUILT_IN: the value only translates the single values of one column onto the allowed values (codes,
              symbols, abbreviations, other words or another language), which the built-in value mapping functions do.
            - OK: anything else, e.g. the value has to be computed from several columns or rows.

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}
            Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type})
            {#if schemaNode.dataType.allowedValues}Allowed values: {schemaNode.dataType.allowedValues}{/if}
            {/if}
            Selected columns: {selectedColumns}
            {#if fileValues}Values of the selected columns:
            {fileValues}
            {/if}
            Reason: {reason}
            """)
    NotFoundReview review(LocalSchemaNodeDTO schemaNode, Object selectedColumns, String fileValues, String reason);
}
