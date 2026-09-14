package bio.cosy.feddb.local.api.schema.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.C;

import java.util.*;

@Entity(name = "datatype")
@Table(name = "datatype")
@Getter
@Setter
@Audited
public class DataTypeEntity extends BaseEntity {

    @Column(name = "description", columnDefinition = "text")
    private String desc;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private DataTypes type;

    @Column(name = "global_id", length = 255, nullable = false, unique = true)
    private String globalDataTypeId;


    // Empty initialize it, JSON columns otherwise return null
    // while "normal" columns return an empty set
    @Column(name = "validations", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<DataTypeValidationDTO> validations = new HashSet<>();

    @Column(name = "allow_null_values")
    @ColumnDefault("true")
    private Boolean allowNullValues = true;

    @Column(name = "is_required")
    private Boolean isRequired = false;

    // Only contains strings for categorical data types
    // e.g. numerical uses min and max validations
    @Column(name = "allowed_values", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<Object> allowedValues = new HashSet<>();

    @Column(name = "options", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<DataTypeOptionsDTO> options = new HashSet<>();

    @Column(name = "mapping", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> mapping = new HashMap<>();
    // This is a mapping of the allowed values to their human-readable form
    // e.g. from "codeX" to "Colorectal Cancer"

    // Bidirectional relationship with SchemaNodeEntity
    // As a deleted datatype breaks the schema we cascade up any changes
    @OneToMany(mappedBy = "dataType", cascade = {CascadeType.ALL}, orphanRemoval = true)
    private Set<SchemaNodeEntity> schemaNodes = new HashSet<>();

    @Override
    public int hashCode() {
        return Objects.hash(desc, name, type, globalDataTypeId, validations, allowedValues, mapping, schemaNodes);
    }

    @Override
    public String toString() {
        return "DataTypeEntity{" +
                "desc='" + desc + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", globalDataTypeId='" + globalDataTypeId + '\'' +
                ", validations=" + validations +
                ", allowedValues=" + allowedValues +
                ", mapping=" + mapping +
                '}';
    }
}
