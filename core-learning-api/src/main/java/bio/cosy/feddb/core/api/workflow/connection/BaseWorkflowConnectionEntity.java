package bio.cosy.feddb.core.api.workflow.connection;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public class BaseWorkflowConnectionEntity extends BaseEntity {

    @Column(name = "input_id", nullable = false)
    private String inputId;
    @Column(name = "output_id", nullable = false)
    private String outputId;

    @Column(name = "is_input_connection", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isInputConnection;
}
