
package de.unihamburg.daibetes.api.app.config.hyperparam;

import bio.cosy.feddb.core.api.app.config.ToolConfigHyperParamDataType;
import bio.cosy.feddb.core.api.app.config.ToolConfigModeType;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_app_hyperparam_configs")
public class FederatedAppHyperParamConfigEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private ToolConfigModeType mode = ToolConfigModeType.BOTH;


    @Column(length = 50)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private ToolConfigHyperParamDataType type;

    @Column(name = "defaultValue")
    private String defaultValue;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;

    private String options = "";

    @Column(name = "min_value")
    private Float minValue;

    @Column(name = "max_value")
    private Float maxValue;

    private String pattern = "";
}
