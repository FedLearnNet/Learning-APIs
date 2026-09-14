package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.access.ModelAccessEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "models")
public class ModelEntity extends BaseEntity {

    @Column(length = 255)
    private String name;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false, unique = true, name = "unique_model_id")
    private UUID uniqueModelId;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "long_description", length = 20000)
    private String longDescription;

    @Column(name = "publish_status")
    @Enumerated(EnumType.STRING)
    private ModelPublishStatus publishStatus = ModelPublishStatus.PRIVATE;

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL)
    private Set<ModelVersionEntity> versions;

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL)
    private Set<ModelAccessEntity> accesses;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;


    @PrePersist
    protected void onCreate() {
        if (this.uniqueModelId == null) {
            this.uniqueModelId = UUID.randomUUID();
        }
    }
}
