package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "code_transformers")
@Getter
@Setter
public class CodeTransformerEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FunctionExecutionMode mode;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private DataTypeNodeDTO goal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_goals", columnDefinition = "jsonb")
    private Map<String, DataTypeNodeDTO> sourceGoals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<Object>> data;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_keys", columnDefinition = "jsonb")
    private List<String> inputKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "return_keys", columnDefinition = "jsonb")
    private List<String> returnKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> files;

    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "image_name", length = 1000)
    private String imageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeTransformerStatus status;

    @OneToMany(mappedBy = "codeTransformer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id DESC")
    private List<PipelineEntity> pipelines = new ArrayList<>();
}
