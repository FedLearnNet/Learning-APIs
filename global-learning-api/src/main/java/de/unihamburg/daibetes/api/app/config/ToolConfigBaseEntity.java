
package de.unihamburg.daibetes.api.app.config;

import bio.cosy.feddb.core.api.app.config.TabularSchemaDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.app.config.ToolConfigModeType;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@MappedSuperclass
public class ToolConfigBaseEntity extends BaseEntity {

    @Column(length = 50)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private ToolConfigDataType type;

    @Enumerated(EnumType.STRING)
    private ToolConfigModeType mode = ToolConfigModeType.BOTH;

    @Column(name = "min_value")
    private String minValue;

    @Column(name = "max_value")
    private String maxValue;

    private String delimiter;
    @Column(name = "has_header")
    private Boolean hasHeader;
    @Column(name = "index_col")
    private Integer indexCol;

    private String shape;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tabular_schema", columnDefinition = "jsonb")
    private TabularSchemaDTO tabularSchema;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;
}
