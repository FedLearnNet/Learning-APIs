package eval.agent.etl.transform.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TransformerWriterReview {
    private String schemaNode;
    private String description;
    private String dataType;

    private boolean found;
    private String notFoundReason;
    private String mappedColumn;
    private List<TransformerWriterTransformation> transformations = new ArrayList<>();
    private List<String> sourceColumns = new ArrayList<>();

    private String importRun;
    private long importedEntries;
    private long rejectedValues;
    private Map<String, Long> importedValues = new LinkedHashMap<>();
    private Map<String, Long> rejections = new LinkedHashMap<>();
}
