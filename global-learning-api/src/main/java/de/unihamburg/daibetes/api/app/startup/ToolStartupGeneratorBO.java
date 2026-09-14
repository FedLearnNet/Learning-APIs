package de.unihamburg.daibetes.api.app.startup;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppType;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class ToolStartupGeneratorBO {
    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    @Location("startup/README.md")
    Template readme;

    @Inject
    @Location("startup/env")
    Template env;

    @Inject
    @Location("startup/requirements.txt")
    Template requirements;

    @Inject
    @Location("startup/app.yml")
    Template modelYml;

    @Inject
    @Location("startup/main.py")
    Template main;

    @Inject
    @Location("startup/config.py")
    Template config;

    @Inject
    @Location("startup/aggregator.py")
    Template aggregator;

    @Inject
    @Location("startup/app.py")
    Template appTemplate;

    @Inject
    @Location("startup/evaluation_app.py")
    Template appEvaluationTemplate;

    @Inject
    @Location("startup/preprocess_app.py")
    Template appPpreprocessTemplate;

    @Inject
    @Location("startup/export_app.py")
    Template appExportTemplate;

    @Inject
    @Location("startup/self_learned_app.py")
    Template appSelfLearnedTemplate;

    @Inject
    @Location("startup/transformer_app.py")
    Template transformerAppTemplate;

    @Inject
    @Location("startup/database_app.py")
    Template appDBAdopterTemplate;

    @Inject
    @Location("startup/Dockerfile")
    Template dockerfile;

    @Inject
    @Location("startup/gitignore")
    Template gitignore;

    public StreamingOutput generateStartupProject(Long appId, ToolStartupGeneratorCreateDTO createDTO, String keycloakId) {
        FederatedAppDetailDTO app = federatedAppBO.getMyAppById(appId, keycloakId);
        String appName = stringToCamelCase(app.getName());
        if (StringUtils.isEmpty(appName)) {
            appName = "MyApp";
        }
        String finalAppName = appName;
        String appContent = getAppTemplate(app, appName);
        return os -> {
            try (var zip = new ZipOutputStream(os)) {
                addReadme(zip, app, appContent);
                addRequirements(zip);
                addEnv(zip, app, createDTO);
                addModelYml(zip, app);
                addMain(zip, app, finalAppName);
                addApp(zip, app, appContent);
                addDockerFile(zip);
                addGitIgnore(zip);
                zip.finish();
            }
        };
    }

    private void addReadme(ZipOutputStream zip, FederatedAppDetailDTO app, String appContent) {
        try {
            String content = readme
                    .data("type", app.getType())
                    .data("example", appContent)
                    .render();
            writeEntry(zip, "README.md", content);
        } catch (Exception e) {
            Log.errorf("Failed to add README.md: %s", e.getMessage());
        }
    }

    private void addRequirements(ZipOutputStream zip) {
        try {
            String content = requirements
                    .render();
            writeEntry(zip, "requirements.txt", content);
        } catch (Exception e) {
            Log.errorf("Failed to add README.md: %s", e.getMessage());
        }
    }

    private void addEnv(ZipOutputStream zip, FederatedAppDetailDTO app, ToolStartupGeneratorCreateDTO createDTO) {
        try {
            String content = env
                    .data("id", app.getId())
                    .data("config", createDTO)
                    .render();
            writeEntry(zip, ".env", content);
        } catch (Exception e) {
            Log.errorf("Failed to add .env: %s", e.getMessage());
        }
    }

    private void addModelYml(ZipOutputStream zip, FederatedAppDetailDTO app) {
        try {
            String content = modelYml
                    .data("app", app)
                    .render();
            writeEntry(zip, "app.yml", content);
        } catch (Exception e) {
            Log.errorf("Failed to add modelYml: %s", e.getMessage());
        }
    }

    private void addMain(ZipOutputStream zip, FederatedAppDetailDTO app, String appName) {
        try {
            String content = main
                    .data("appName", appName)
                    .data("type", app.getType())
                    .data("supportsFL", app.getSupportsFederatedLearning())
                    .render();
            writeEntry(zip, "main.py", content);
        } catch (Exception e) {
            Log.errorf("Failed to add main.py: %s", e.getMessage());
        }
    }

    private String getAppTemplate(FederatedAppDetailDTO app, String appName) {
        Template instance = switch (app.getType()) {
            case PRE_PROCESSING -> appPpreprocessTemplate;
            case POST_PROCESSING -> appPpreprocessTemplate;
            case EVALUATION -> appEvaluationTemplate;
            case SELF_LEARNED -> appSelfLearnedTemplate;
            case DATA_TRANSFORMATION -> transformerAppTemplate;
            case EXTRACTOR -> appDBAdopterTemplate;
            case EXPORT -> appExportTemplate;
            default -> appTemplate;
        };
        return instance.data("name", appName)
                .data("supportsFL", app.getSupportsFederatedLearning())
                .render();
    }

    private void addApp(ZipOutputStream zip, FederatedAppDetailDTO app, String appContent) {
        try {

            if (app.getType().equals(FederatedAppType.DATA_TRANSFORMATION)) {
                writeEntry(zip, "app.py", appContent);
            } else {
                if (app.getSupportsFederatedLearning()) {
                    String aggregatorContent = aggregator
                            .render();
                    writeEntry(zip, "aggregator.py", aggregatorContent);
                }
                writeEntry(zip, "app.py", appContent);
                String configContent = config
                        .render();
                writeEntry(zip, "config.py", configContent);
            }
        } catch (Exception e) {
            Log.errorf("Failed to add app.py: %s", e.getMessage());
        }
    }


    private void addDockerFile(ZipOutputStream zip) {
        try {
            String content = dockerfile
                    .render();
            writeEntry(zip, "Dockerfile", content);
        } catch (Exception e) {
            Log.errorf("Failed to add Dockerfile: %s", e.getMessage());
        }
    }

    private void addGitIgnore(ZipOutputStream zip) {
        try {
            String content = gitignore
                    .render();
            writeEntry(zip, ".gitignore", content);
        } catch (Exception e) {
            Log.errorf("Failed to add .gitignore: %s", e.getMessage());
        }
    }

    private void writeEntry(ZipOutputStream zip, String path, String content) throws IOException {
        ZipEntry e = new ZipEntry(path);
        zip.putNextEntry(e);
        zip.write(content.getBytes());
        zip.closeEntry();
    }

    private String stringToCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }
}
