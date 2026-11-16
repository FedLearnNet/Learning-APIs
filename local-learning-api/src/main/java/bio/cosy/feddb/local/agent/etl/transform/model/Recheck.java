package bio.cosy.feddb.local.agent.etl.transform.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Recheck {
    private String hint;
    private List<String> columns;
}
