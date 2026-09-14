package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryClientResponseDTO {
    private FedDBClientResponseType type;
    private String globalUniqueQueryId;
    private Long count;
    private String error;


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
