package bio.cosy.feddb.local.api.schema.datatype;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;

@Getter
public enum DatatypeFormTypeEnum {

    @JsonAlias({"number", "NUMBER"})
    NUMBER,

    @JsonAlias({"radio", "RADIO"})
    RADIO,

    @JsonAlias({"select", "SELECT"})
    SELECT,

    @JsonAlias({"file", "FILE"})
    FILE,

    @JsonAlias({"date", "DATE"})
    DATE,

    @JsonAlias({"date-time", "DATETIME", "DATE_TIME"})
    DATE_TIME,

    @JsonAlias({"text", "TEXT"})
    TEXT;


}
