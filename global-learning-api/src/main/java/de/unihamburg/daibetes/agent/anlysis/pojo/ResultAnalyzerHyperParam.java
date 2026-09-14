package de.unihamburg.daibetes.agent.anlysis.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultAnalyzerHyperParam {
    private String name;
    private String description;
    private String value;
}
