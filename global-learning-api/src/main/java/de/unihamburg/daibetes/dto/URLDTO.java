package de.unihamburg.daibetes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class URLDTO {
    @URL(message = "Invalid URL format")
    private String url;

}
