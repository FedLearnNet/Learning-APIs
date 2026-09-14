package unit.importer;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectorDTODateDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCreatedAtWithoutMilliseconds() throws Exception {
        ConnectorDTO connector = objectMapper.readValue(
                """
                {
                  "name": "us-130",
                  "createdAt": "2026-04-15T09:33:04Z"
                }
                """,
                ConnectorDTO.class
        );

        assertEquals(Instant.parse("2026-04-15T09:33:04Z"), connector.getCreatedAt().toInstant());
    }

    @Test
    void deserializesCreatedAtWithMilliseconds() throws Exception {
        ConnectorDTO connector = objectMapper.readValue(
                """
                {
                  "name": "us-130",
                  "createdAt": "2026-04-15T09:33:04.123Z"
                }
                """,
                ConnectorDTO.class
        );

        assertEquals(Instant.parse("2026-04-15T09:33:04.123Z"), connector.getCreatedAt().toInstant());
    }
}
