package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryClientResponseDTO {
    private FedDBClientResponseType type;
    private String globalUniqueQueryId;
    private Long count;
    private String error;
    private SystemComputingInfoDTO system;
    private Set<ExecutionMode> supportedModes;
    // the platform may show the client's Keycloak name, only for TRE
    private Boolean shareName;


    public QueryClientResponseDTO(FedDBClientResponseType type, String globalUniqueQueryId, Long count, String error) {
        this(type, globalUniqueQueryId, count, error, null, null, null);
    }

    public static QueryClientResponseDTO createErrorResponse(String errorMessage, String globalUniqueQueryId) {
        QueryClientResponseDTO dto = new QueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.ERROR);
        dto.setError(errorMessage);
        dto.setCount(0L);
        dto.setGlobalUniqueQueryId(globalUniqueQueryId);
        return dto;
    }

    public static QueryClientResponseDTO createResponse(Long count, String globalUniqueQueryId) {
        QueryClientResponseDTO dto = new QueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.QUERY);
        dto.setError(null);
        dto.setCount(count);
        dto.setGlobalUniqueQueryId(globalUniqueQueryId);
        return dto;
    }

    @Override
    public String toString() {
        return "QueryClientResponseDTO{" +
                "type=" + type +
                ", globalUniqueQueryId='" + globalUniqueQueryId + '\'' +
                ", count=" + count +
                ", error='" + error + '\'' +
                '}';
    }
}
