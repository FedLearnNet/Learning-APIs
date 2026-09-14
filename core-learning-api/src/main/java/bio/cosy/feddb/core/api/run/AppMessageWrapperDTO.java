package bio.cosy.feddb.core.api.run;

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
