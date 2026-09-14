package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import de.unihamburg.daibetes.api.app.config.ToolConfigExternalStandardHandler;
import de.unihamburg.daibetes.api.app.tag.FederatedAppTagBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticClassGenerator;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticUpdateDTO;
import de.unihamburg.daibetes.dto.URLDTO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class FederatedAppServiceImpl implements FederatedAppService {


    @Inject
    UserIdentity userIdentity;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    FederatedAppTagBO federatedAppTagBO;

    @Inject
    ToolConfigExternalStandardHandler toolConfigExternalStandardHandler;

    @Override
    public List<FederatedAppDTO> list() {
        return federatedAppBO.getAll();
    }

    @Override
    public List<FederatedAppDetailDTO> listMyApps() {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedAppBO.listMyApps(keycloakId);
    }

    @Override
    public FederatedAppDetailDTO getMyApps(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedAppBO.getMyAppById(id, keycloakId);
    }

    @Override
    public FederatedAppDetailDTO retrieve(String id) {
        String keycloakId = userIdentity.getKeycloakId();
        Object idOrSlug = idOrSlug(id);
        return this.federatedAppBO.getAppObject(idOrSlug, keycloakId);
    }

    @Override
    public PydanticUpdateDTO retrievePydantic(Long id, Long appId) {
        String keycloakId = userIdentity.getKeycloakId();
        FederatedAppVersionDTO app = this.federatedAppBO.getMyAppVersionById(id, appId, keycloakId);
        if (app.getAppConfig() == null) {
            throw new NotFoundException("No app config found");
        }
        return PydanticClassGenerator.generatePydanticClasses(app.getAppConfig());
    }

    @Override
    @Transactional
    public Response create(AppCreateDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        FederatedAppDTO createdDTO = federatedAppBO.create(dto, keycloakId);
        return Response.status(Response.Status.CREATED)  // 201 status
                .entity(createdDTO)
                .build();
    }

    @Override
    @Transactional
    public FederatedAppDetailDTO update(String id, FederatedAppDetailDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        Object idOrSlug = idOrSlug(id);
        return federatedAppBO.update(idOrSlug, dto, keycloakId);
    }

    @Override
    @Transactional
    public FederatedAppDetailDTO publish(Long id, FederatedAppPublishDTO publishDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedAppBO.publish(id, keycloakId, publishDTO);
    }

    @Override
    public URLDTO saveAppAsJson(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return federatedAppBO.saveToolAsJson(id, keycloakId);
    }

    @Override
    public URLDTO replaceBuildInfoInJson(Long id) {
        return federatedAppBO.replaceBuildInfoInJson(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response delete(String id) {
        String keycloakId = userIdentity.getKeycloakId();
        Object idOrSlug = idOrSlug(id);
        federatedAppBO.delete(idOrSlug, keycloakId);
        return Response.status(Response.Status.OK)
                .build();
    }

    @Override
    public Response checkForUserAuth(FederatedAppAuthCheckDTO appAuthCheckDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        federatedAppBO.checkUserAccess(appAuthCheckDTO, keycloakId);
        return Response.status(Response.Status.OK)
                .build();
    }

    @Override
    public List<FederatedAppTagDTO> getTags() {
        return federatedAppTagBO.getAll();
    }

    @Override
    public List<ToolConfigDTO> getPredefinedConfig() {
        return toolConfigExternalStandardHandler.loadAll();
    }

    private Object idOrSlug(String id) {
        Object idOrSlug = id;
        if (StringUtils.isNumeric(id)) {
            idOrSlug = Long.parseLong(id);
        }
        return idOrSlug;
    }
}
