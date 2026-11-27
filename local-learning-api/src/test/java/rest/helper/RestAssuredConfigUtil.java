package rest.helper;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

import io.restassured.RestAssured;
import io.restassured.mapper.ObjectMapperType;

public class RestAssuredConfigUtil {

    public static void initMapper() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .defaultDateFormat(new StdDateFormat().withColonInTimeZone(true))
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .build();
        RestAssured.config = RestAssured.config()
                .objectMapperConfig(
                        RestAssured.config().getObjectMapperConfig()
                                .defaultObjectMapperType(ObjectMapperType.JACKSON_3)
                                .jackson3ObjectMapperFactory((cls, charset) -> objectMapper)
                );
    }

}
