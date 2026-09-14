package bio.cosy.feddb.local.api.cohort.member;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.representations.idm.AbstractUserRepresentation;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CohortAvailableUserDTO {
    protected String id;
    protected String username;
    protected String firstName;
    protected String lastName;
    protected String email;

    public static CohortAvailableUserDTO of(AbstractUserRepresentation user) {
        return new CohortAvailableUserDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }
}
