package bio.cosy.feddb.local.api.query;

import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class QueryResultWrapperDTO {
    private Long queryId;

    private List<QueryResultDTO> result;

    private String patientMatchSql;

    @Override
    public String toString() {
        String resultString = result == null
                ? "[]"
                : result.stream().map(QueryResultDTO::toString).collect(Collectors.joining(", ", "[", "]"));
        return "QueryResultWrapperDTO{" +
                "queryId='" + queryId + '\'' +
                ", result=" + resultString +
                '}';
    }
}
