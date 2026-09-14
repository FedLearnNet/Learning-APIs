package rest.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.restassured.RestAssured;
import io.restassured.mapper.ObjectMapperType;

public class RestAssuredConfigUtil {

    public static void initMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestAssured.config = RestAssured.config()
                .objectMapperConfig(
                        RestAssured.config().getObjectMapperConfig()
                                .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
                                .jackson2ObjectMapperFactory((cls, charset) -> objectMapper)
                );
    }

}
