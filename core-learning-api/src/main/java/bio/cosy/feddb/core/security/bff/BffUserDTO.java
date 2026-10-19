package bio.cosy.feddb.core.security.bff;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class BffUserDTO {
    private String username;
    private String email;
    private Boolean emailVerified;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
