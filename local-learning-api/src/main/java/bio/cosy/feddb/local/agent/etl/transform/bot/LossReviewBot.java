package bio.cosy.feddb.local.agent.etl.transform.bot;

import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface LossReviewBot {

    @UserMessage("""
            An agent mapped the selected columns onto the schema node below. Its chain of transformers is valid, but it
            loses information, as listed below. Judge every loss with these rules:
            - A value becomes empty only when it carries no information, e.g. a blank or a placeholder for an unknown
              entry. A value stating a fact, e.g. not measured, none, normal or negative, is information: when an
              allowed value means the same (compare the shares of the description with the counts of the file), or
              the data type accepts the value itself, it becomes that value and not empty.
            - Different values become one only when the schema node cannot hold the difference, e.g. it has fewer
              allowed values. When the data type accepts the finer values, e.g. its pattern allows them, they stay.
            Answer keep true when every loss follows the rules, else keep false and the reason: which values should
            become what instead.

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}
            Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type})
            {#if schemaNode.dataType.allowedValues}Allowed values: {schemaNode.dataType.allowedValues}{/if}
            {#if schemaNode.dataType.validations}Validations: {#for v in schemaNode.dataType.validations}{v.name} {v.validator}; {/for}{/if}
            {/if}
            {#if fileValues}Values of the selected columns in the whole file:
            {fileValues}
            {/if}
            Losses: {losses}
            """)
    LossReview review(LocalSchemaNodeDTO schemaNode, String fileValues, String losses);
}
