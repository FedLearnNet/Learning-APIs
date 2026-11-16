package bio.cosy.feddb.core.execution.run.dto;

import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppMessageWrapperDTO<T> {

    private AppMessageTypeEnum type;
    private T message;
    private AppRunTypeEnum runType = AppRunTypeEnum.TEST_RUN;

    public AppMessageWrapperDTO(AppMessageTypeEnum type, T message) {
        this.type = type;
        this.message = message;
    }

}
