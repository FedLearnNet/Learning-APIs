package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class QueryClientResponseDTO {
    private FedDBClientResponseType type;
    private String globalUniqueQueryId;
    private Long count;
    private SystemComputingInfoDTO system;
    private Set<ExecutionMode> supportedModes;
    // the platform may show the client's Keycloak name, only for TRE
    private Boolean shareName;

    /**
     * Rejected query; the reason stays on the client, it could describe its data.
     */
    public static QueryClientResponseDTO createErrorResponse(String globalUniqueQueryId) {
        QueryClientResponseDTO dto = new QueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.ERROR);
        dto.setCount(0L);
        dto.setGlobalUniqueQueryId(globalUniqueQueryId);
        return dto;
    }

    /**
     * Answered query with the number of matched patients, 0 when below the local threshold.
     */
    public static QueryClientResponseDTO createResponse(Long count, String globalUniqueQueryId) {
        QueryClientResponseDTO dto = new QueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.QUERY);
        dto.setCount(count);
        dto.setGlobalUniqueQueryId(globalUniqueQueryId);
        return dto;
    }
}
