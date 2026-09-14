package de.unihamburg.daibetes.api.umls.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UMLSRelationshipsResultDTO {
    private String ui;
    private boolean suppressible;
    private String sourceUi;
    private boolean obsolete;
    private boolean sourceOriginated;
    private String rootSource;
    private String groupId;
    private int attributeCount;
    private String classType;
    private String relatedFromId;
    private String relatedFromIdName;
    private String relationLabel;
    private String additionalRelationLabel;
    private String relatedId;
    private String relatedIdName;
}
