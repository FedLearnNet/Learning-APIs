package de.unihamburg.daibetes.api.development.testembed.pydantic;

import lombok.Data;

@Data
public class PydanticUpdateDTO {
    String hyperparam;
    String input;
    String output;
}
