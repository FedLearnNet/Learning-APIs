package bio.cosy.feddb.core.base;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class FlexibleDateDeserializer extends ValueDeserializer<Date> {

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return new Date(parser.getLongValue());
        }
        if (token != JsonToken.VALUE_STRING) {
            return (Date) context.handleUnexpectedToken(Date.class, parser);
        }

        String rawValue = parser.getString();
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(rawValue.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return Date.from(timestamp.toInstant());
        } catch (DateTimeException e) {
            throw context.weirdStringException(
                    rawValue,
                    Date.class,
                    "Expected ISO-8601 offset timestamp with optional milliseconds");
        }
    }
}
