package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineEntity;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "pipeline")
public class PipelineEntity extends BasePipelineEntity<PipelineStepEntity> {

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PipelineStepEntity> steps = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_transformer_id", nullable = false)
    private CodeTransformerEntity codeTransformer;
}
