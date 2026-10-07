package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;

import java.util.List;

public interface VisitColumnAgent {

    @UserMessage("""
            Your job is to set up the optional time series mapping of the schema node below, whose value comes from
            the column '{mappedColumn}'. The export has several rows per patient, e.g. one per encounter, admission
            or measurement, and the patient id column is '{patientIdColumn}'.
            - visitIdMapping: the column that identifies the visit a row belongs to, so the values of different
              visits of one patient are kept apart.
            - visitTimestampMapping: the column with the date or time the value was recorded.
            - timestampFormat: the DateTimeFormatter pattern of the timestamp values, if they are not ISO dates.
            Leave a field empty when no column fits; a wrong column is worse than none. Use exact column names.
            {#if instructions}Instructions of the user: {instructions}{/if}

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}

            Columns with example values:
            {#for column in columns}
            - {column}
            {/for}
            """)
    @Agent(description = "Selects the visit id and timestamp columns of a mapped schema node", outputKey = "visitMapping")
    VisitMapping select(LocalSchemaNodeDTO schemaNode, String mappedColumn, String patientIdColumn,
                        List<String> columns, String instructions);
}
