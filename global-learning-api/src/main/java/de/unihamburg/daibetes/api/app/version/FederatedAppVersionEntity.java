package de.unihamburg.daibetes.api.app.version;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.base.BaseStoreVersionEntity;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.audit.ToolAuditEntity;
import de.unihamburg.daibetes.api.app.config.hyperparam.FederatedAppHyperParamConfigEntity;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.runs.test.TestRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "federated_app_versions")
public class FederatedAppVersionEntity extends BaseStoreVersionEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_id", nullable = false)
    private FederatedAppEntity federatedApp;

    @Column(name = "publish_status", length = 50)
    @Enumerated(EnumType.STRING)
    private PublishStatus versionPublishStatus = PublishStatus.UNPUBLISHED;

    @Column(name = "publish_hash", length = 100, unique = true)
    private String publishHash;

    @Column(name = "publish_info", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private AppPublishInfoDTO publishInfo;

    @ColumnDefault("0")
    @Column(name = "certification_level")
    private int certificationLevel = 0;

    @ColumnDefault("false")
    @Column(name = "needs_internet_access")
    private Boolean needsInternetAccess = false;

    @ColumnDefault("false")
    @Column(name = "needs_host_access")
    private Boolean needsHostAccess = false;

    @Column(name = "image_name", nullable = true)
    private String imageName;

    @Column(name = "short_description", length = 5000)
    private String shortDescription;

    @Column(name = "long_description", length = 20000)
    private String longDescription;


    @OneToMany(mappedBy = "federatedAppVersion", cascade = CascadeType.ALL)
    private Set<TestRunEntity> testRuns;

    @OneToMany(mappedBy = "federatedAppVersion", cascade = CascadeType.ALL)
    private Set<FederatedAppOutputConfigEntity> outputConfig;

    @OneToMany(mappedBy = "federatedAppVersion", cascade = CascadeType.ALL)
    private Set<FederatedAppInputConfigEntity> inputConfig;

    @OneToMany(mappedBy = "federatedAppVersion", cascade = CascadeType.ALL)
    private Set<FederatedAppHyperParamConfigEntity> hyperParamConfig;

    @OneToMany(mappedBy = "appVersion", cascade = CascadeType.ALL)
    private Set<ToolAuditEntity> audits;

    public boolean hasImage() {
        return imageName != null && !imageName.isEmpty();
    }

}

