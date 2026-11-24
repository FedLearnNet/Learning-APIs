package de.unihamburg.daibetes.agent.audit.source;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolSourceFile {
    private String path;
    private String content;
}
