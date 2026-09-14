package de.unihamburg.daibetes.api.feedback.issue;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GitlabCreateIssueDTO {

    @NotBlank(message = "Title must not be blank")
    private String title;

    @NotBlank(message = "Description must not be blank")
    private String description;
}
