package bio.cosy.feddb.core.api.run.message;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public class BaseRunMessageEntity extends BaseEntity {

    private String process;

    @Enumerated(EnumType.STRING)
    private RunMessageTypes type;

    @Column(columnDefinition = "TEXT")
    private String message;
}
