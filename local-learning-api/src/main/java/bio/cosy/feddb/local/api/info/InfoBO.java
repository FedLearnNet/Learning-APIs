package bio.cosy.feddb.local.api.info;

import bio.cosy.feddb.core.api.info.InfoDTO;
import bio.cosy.feddb.core.api.info.ReleaseVersion;
import bio.cosy.feddb.local.services.GlobalAPIInfoService;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class InfoBO {

    @ConfigProperty(name = "quarkus.application.version")
    String version; //its the pom.xml version

    @ConfigProperty(name = "quarkus.rest-client.global-api.url")
    String globalApiUrl;

    @Inject
    @RestClient
    GlobalAPIInfoService platformAPIInfoService;

    public LocalInfoDTO getInfo() {
        LocalInfoDTO dto = new LocalInfoDTO(version, globalWebUrl());
        setPlatformVersion(dto);
        return dto;
    }

    private void setPlatformVersion(LocalInfoDTO dto) {
        try {
            dto.setPlatformVersion(globalInfo().getVersion());
            dto.setPlatformVersionNewer(ReleaseVersion.isNewer(dto.getPlatformVersion(), version));
        } catch (RuntimeException e) {
            Log.errorf("Platform version unknown: %s", e.getMessage());
        }
    }

    @CacheResult(cacheName = "global-info")
    InfoDTO globalInfo() {
        return platformAPIInfoService.getInfo();
    }

    String globalWebUrl() {
        return globalApiUrl.replaceAll("/+$", "").replaceAll("/api$", "");
    }
}
