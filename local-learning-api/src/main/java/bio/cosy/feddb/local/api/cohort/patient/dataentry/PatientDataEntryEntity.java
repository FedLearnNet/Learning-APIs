package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "patient_data",
        indexes = {
                @Index(name = "idx_patient_data_patient_id", columnList = "patient_id"),
                @Index(name = "idx_patient_data_patient_id_id", columnList = "patient_id, id"),
                @Index(name = "idx_patient_data_schema_node_id", columnList = "schema_node_id"),
        })
@Audited
@Getter
@Setter
public class PatientDataEntryEntity extends BaseEntity {
    // We do not extend BaseEntity as this would bloat up the table.
    // This is the biggest table in the system and it has a LOT of rows specifically:
    // #rows ~ #patients * #schemanodes * (#timepoints+#visits) entries.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Audited
    private PatientEntity patient;
    // contains external ID, cohort, cohort contains schema nodes, ...

    // Connect to one schema node of the patient's cohort
    // A schemanode can be reused by the same patient (timeseries data), multiple patients, ...
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_node_id", nullable = false)
    private SchemaNodeEntity schemaNode;
    // BO must check if that schemaNode is part of patient.cohort.schemaNodes!

    // Value information, only one of these is set, depending on the type of the schema node
    @Column(name = "value_int")
    private Long valueInt;
    @Column(name = "value_float")
    private Float valueFloat;
    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    // For now we use TEXT and BYTEA, they however can only have 1GB in size
    // TODO: use the whole large objects system of PostgreSQL
    // java persistence does not support it nicely, so we'd need a lot of custom functions
    // and be careful with the query, postgresql uses large object pointers as values!
    @Column(name = "value_string", columnDefinition = "TEXT")
    private String valueString;
    @Column(name = "value_blob", columnDefinition = "TEXT")
    private String valueBlob;
    // b64 encoded string of the binary data
    @Column(name = "value_date")
    private LocalDate valueDate;
    @Column(name = "value_date_time")
    private Instant valueDateTime;

    //If allow null values is set, we need to be able to distinguish between an empty value and a null value, as both are accepted
    @Column(name = "is_null_value")
    private Boolean isNullValue = false;
    // All Meta information
    // Harcoded entries used in querying etc.
    // The same visit might have multiple entries of the same schema nodes, e.g.
    // drug name, drug dosage
    // A, 5
    // B, 10
    // In this case in this entity format we could normally not map the dosage correctly
    // This is why we use the grouping information given when schema nodes have the same
    // parent node. So if the schema nodes belong together and the values A and 5
    // come together in the same import entry, they get the same ID:
    //   parentNodeId + connectorID + ImportRunId + importDataEntryId
    // This importDataEntryId can also just be the index in the list of data entries of the import
    // For data added via the frontend, this is
    //   parentNodeId + RequestId
    // TODO: refactor and simplify this and change the comment
    @Column(name = "import_schema_group_id")
    private String importSchemaGroupId;

    @Column(name = "visit_id")
    private String visitId;
    @Column(name = "visit_timestamp")
    private Instant visitTimestamp;
    @Column(name = "visit_timestamp_format")
    private String visitTimestampFormat;

    // Dynamic meta data entries
    @OneToMany(mappedBy = "patientDataEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited
    private Set<MetaPatientDataEntryEntity> metaDataEntries = new HashSet<>();
}
