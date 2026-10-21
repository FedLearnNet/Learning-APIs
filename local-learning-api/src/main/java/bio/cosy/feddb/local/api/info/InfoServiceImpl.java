package bio.cosy.feddb.local.api.info;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InfoServiceImpl implements InfoService {

    @Inject
    InfoBO infoBO;

    @Override
    public LocalInfoDTO getInfo() {
        return infoBO.getInfo();
    }
}
