package bio.cosy.feddb.local.api.statistics;

import bio.cosy.feddb.core.api.socket.DataStatisticsDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class LocalDataStatisticsDTO  extends DataStatisticsDTO {

    private List<Long> cohortIds;
    private List<Long> patientIds;
}
