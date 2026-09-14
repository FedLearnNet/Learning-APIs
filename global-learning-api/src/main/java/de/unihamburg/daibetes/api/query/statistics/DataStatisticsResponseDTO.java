package de.unihamburg.daibetes.api.query.statistics;

import bio.cosy.feddb.core.api.socket.DataStatisticsDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataStatisticsResponseDTO extends BaseDTO {
    private DataStatisticsDTO statistics;
    private Long queryId;
    private String randomClinicId;
    private UUID requestId;
}
