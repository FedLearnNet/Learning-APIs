package de.unihamburg.daibetes.agent.audit.source;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Text files of a repository archive plus the paths that were skipped
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolSourceSnapshot {
    private String archiveUrl;
    private List<ToolSourceFile> files;
    private List<String> skippedPaths;
}
