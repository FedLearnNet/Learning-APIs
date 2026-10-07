package bio.cosy.feddb.local.agent.etl.transform.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitMapping {

    @Description("""
            The column that identifies the visit, encounter or admission a row belongs to, e.g. an encounter or
            admission id. Never the patient id column. Empty if no column identifies the visit.
            """)
    private String visitIdMapping;

    @Description("""
            The column with the date or time the value of the schema node was recorded, e.g. the measurement time of a
            lab value or the admission time of admission data. Empty if there is none.
            """)
    private String visitTimestampMapping;

    @Description("""
            The java.time.format.DateTimeFormatter pattern of the timestamp values, e.g. dd.MM.yyyy or
            yyyy-MM-dd HH:mm:ss. Empty if the values are ISO dates or there is no timestamp column.
            """)
    private String timestampFormat;
}
