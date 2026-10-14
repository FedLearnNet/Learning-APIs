package bio.cosy.feddb.local.agent.etl.transform.bot;

import bio.cosy.feddb.local.agent.etl.transform.memory.ColumnSelectMemory;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerColumnTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.RequestScoped;

import java.util.List;

@RegisterAiService(chatMemoryProviderSupplier = ColumnSelectMemory.class)
@RequestScoped
public interface ColumnSelectBot {

    String NAME = "selectColumns";

    @UserMessage("""
            Your job is to select the columns which are needed to calculate the value of the schema node below.
            Pick every column the value depends on (e.g. both dates for a duration), but no column that only looks similar.
            A column whose values are coded or written differently (codes, other words or language, units) still holds
            the information, its values are converted later. The share of values that fit unconverted only decides
            whether a conversion is needed, it is no reason to leave a column out. A cell can also hold several values
            joined by a separator (e.g. a list of codes); such a column still holds the information, it is split later.
            When the description lists the share of each value, prefer the column whose values have about the same shares.
            Return the exact column names, or an empty list if no column holds the information. Before you answer with
            an empty list, look at the values of the columns that could still hold it with describeColumns.
            The tool lists the available transformer functions, in case this helps to judge which inputs are needed.
            If several columns fit equally well, ask the user.
            {#if instructions}Instructions of the user: {instructions}{/if}

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type}){/if}

            Candidate columns, ranked by name similarity, meaning and how many of their values fit unconverted:
            {#for candidate in candidates}
            - {candidate.column}: {candidate.summary}
            {/for}

            All columns with example values, in case the value needs other columns than the candidates, e.g. a column
            whose cells hold several values:
            {#for column in columns}
            - {column}
            {/for}
            """)
    @ToolBox({TransformerListTool.class, TransformerColumnTool.class, TransformerUserTool.class})
    List<String> select(TableSample previewData, LocalSchemaNodeDTO schemaNode, String instructions,
                        List<ColumnCandidate> candidates, List<String> columns);
}
