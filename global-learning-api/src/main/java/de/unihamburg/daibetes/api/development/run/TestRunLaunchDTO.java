package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunLaunchDTO {
    private Long appId;
    private AppMessageWrapperDTO<StartRunDTO> message;
}
