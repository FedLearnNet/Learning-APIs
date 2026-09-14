package de.unihamburg.daibetes.helper;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.base.BaseDTO;

public class ToolExternalCleaner {


    public static FederatedAppDetailDTO cleanup(FederatedAppDetailDTO app) {
        if (app == null) {
            return null;
        }

        app.setPublishInfo(null);
        app.setShortDescription(null);
        app.setLongDescription(null);
        app.setNeedsInternetAccess(null);
        app.setNeedsHostAccess(null);
        app.setImageName(null);
        app.setPublishStatus(null);

        app.setTags(null);
        app.setCertificationLevel(null);

        app.setLatestUnpublishedVersionId(null);
        app.setLatestVersionId(null);
        app.setLatestVersion(null);
        app.setAddedToWorkflowCount(0);
        app.setLastAddedToWorkflow(null);
        app.setCount(0);
        app.setAverage(0);
        app.setAppConfig(null);

        setBaseValuesNull(app);
        if (app.getAuthors() != null) {
            app.setAuthors(app.getAuthors().stream()
                    .peek(ToolExternalCleaner::setBaseValuesNull)
                    .peek(a -> a.setFederatedAppId(null))
                    .distinct()
                    .toList());
        }
        if (app.getVersions() != null) {
            app.setVersions(app.getVersions().stream()
                    .filter(v -> v.getVersionPublishStatus().equals(PublishStatus.PUBLISHED))
                    .peek(ToolExternalCleaner::setBaseValuesNull)
                    .peek(v -> {
                        v.getAppConfig()
                                .setInput(v.getAppConfig()
                                        .getInput()
                                        .stream()
                                        .peek(ToolExternalCleaner::setBaseValuesNull)
                                        .toList()
                                );
                        v.getAppConfig()
                                .setHyperparams(v.getAppConfig()
                                        .getHyperparams()
                                        .stream()
                                        .peek(ToolExternalCleaner::setBaseValuesNull)
                                        .toList()
                                );
                        v.getAppConfig()
                                .setOutput(v.getAppConfig()
                                        .getOutput()
                                        .stream()
                                        .peek(ToolExternalCleaner::setBaseValuesNull)
                                        .toList()
                                );
                    })
                    .toList());
        }


        return app;
    }

    public static void setBaseValuesNull(BaseDTO dto) {
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setId(null);
        dto.setVersion(null);
    }
}
