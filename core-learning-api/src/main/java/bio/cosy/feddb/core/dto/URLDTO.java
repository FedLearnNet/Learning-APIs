package bio.cosy.feddb.core.dto;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Setter
@Getter
public class URLDTO{
    @URL(message = "Invalid URL format")
    private String url;

}
