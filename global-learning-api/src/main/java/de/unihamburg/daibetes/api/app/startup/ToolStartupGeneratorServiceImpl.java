package de.unihamburg.daibetes.api.app.startup;

import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ToolStartupGeneratorServiceImpl implements ToolStartupGeneratorService {

    @Inject
    ToolStartupGeneratorBO toolStartupGeneratorBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    public Response generatorStartup(Long id, ToolStartupGeneratorCreateDTO createDTO) {

        String keycloakId = userIdentity.getKeycloakId();
        return Response.ok(toolStartupGeneratorBO.generateStartupProject(id, createDTO, keycloakId))
                .header("Content-Disposition", "attachment; filename=dev-starter.zip")
                .header("Cache-Control", "no-store")
                .build();
    }
}
