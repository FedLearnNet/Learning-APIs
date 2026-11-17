package de.unihamburg.daibetes.api.build.pipeline;

import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.services.git.GitRefsService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ToolSourceCheckBO {

    // Branch the build pipeline clones.
    static final String BUILT_BRANCH = "refs/heads/main";

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    // Each advertised ref is "<commit> <ref>", ended by a newline or, on the first ref, by NUL and capabilities.
    private static final Pattern BRANCH_HEAD = Pattern.compile("([0-9a-f]{40}) " + Pattern.quote(BUILT_BRANCH) + "[\\x00\\n]");

    @Inject
    GitRefsService gitRefsService;

    public boolean isOutdated(FederatedAppVersionEntity version) {
        String sourceUrl = version.getFederatedApp().getSourceUrl();
        Optional<String> builtCommit = builtCommit(version.getImageName());
        if (StringUtils.isBlank(sourceUrl) || builtCommit.isEmpty()) {
            return false;
        }
        Optional<String> sourceCommit = sourceCommit(sourceUrl);
        if (sourceCommit.isEmpty() || sourceCommit.get().equals(builtCommit.get())) {
            return false;
        }
        Log.warnf("Image %s of tool version %d differs from its source: main of %s is at %s; rebuilding",
                version.getImageName(), version.getId(), sourceUrl, sourceCommit.get());
        return true;
    }

    private Optional<String> builtCommit(String imageName) {
        if (StringUtils.isBlank(imageName)) {
            return Optional.empty();
        }
        int tagStart = imageName.lastIndexOf(':');
        if (tagStart < imageName.lastIndexOf('/')) {
            return Optional.empty();
        }
        String tag = imageName.substring(tagStart + 1);
        return COMMIT.matcher(tag).matches() ? Optional.of(tag) : Optional.empty();
    }

    private Optional<String> sourceCommit(String sourceUrl) {
        try {
            Optional<String> commit = branchHead(gitRefsService.advertisedRefs(sourceUrl));
            if (commit.isEmpty()) {
                Log.warnf("Cannot check source of %s: no %s; keeping its image", sourceUrl, BUILT_BRANCH);
            }
            return commit;
        } catch (IOException | IllegalArgumentException e) {
            Log.warnf("Cannot check source of %s: %s; keeping its image", sourceUrl, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<String> branchHead(String advertisedRefs) {
        if (advertisedRefs == null) {
            return Optional.empty();
        }
        Matcher matcher = BRANCH_HEAD.matcher(advertisedRefs);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
