package de.unihamburg.daibetes.api.info;

import bio.cosy.feddb.core.api.info.InfoDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InfoServiceImpl implements InfoService {

    @ConfigProperty(name = "quarkus.application.version")
    String version; //its the version of pom.xml

    @Inject
    StartupImportsBO startupImportsBO;

    @Override
    public InfoDTO getInfo() {
        InfoDTO dto = new InfoDTO();
        dto.setVersion(version);
        dto.setStartupImports(startupImportsBO.loadState());
        return dto;
    }
}
