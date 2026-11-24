package de.unihamburg.daibetes.agent.audit.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSourceArchiveServiceTest {

    private final ToolSourceArchiveService service = new ToolSourceArchiveService();

    @Test
    void githubArchive() {
        assertEquals(List.of("https://github.com/FedLearnNet/tool-x/archive/abc.zip"),
                service.archiveUrls("https://github.com/FedLearnNet/tool-x.git/", "abc"));
    }

    @Test
    void gitlabArchiveWithFallback() {
        assertEquals(List.of("https://gitlab.cosy.bio/group/apps/gmm/-/archive/main/gmm-main.zip",
                        "https://gitlab.cosy.bio/group/apps/gmm/archive/main.zip"),
                service.archiveUrls("https://gitlab.cosy.bio/group/apps/gmm", "main"));
    }

    @Test
    void nonHttpIgnored() {
        assertTrue(service.archiveUrls("file:///etc/passwd", "main").isEmpty());
        assertTrue(service.archiveUrls("git@github.com:a/b.git", "main").isEmpty());
    }
}
