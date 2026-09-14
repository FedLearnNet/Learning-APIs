package bio.cosy.feddb.local.api.information;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserInformationServiceImpl implements UserInformationService {

    @Inject
    UserInformationServiceBO userInformationServiceBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    public UserInformationDTO getBaseUserInformation() {
        return userInformationServiceBO.getBaseUserInformation(userIdentity.getKeycloakId());
    }
}
