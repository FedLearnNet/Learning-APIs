package bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata;

import bio.cosy.feddb.local.api.file.FileEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.time.LocalDate;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;

@Entity
@Table(name = "meta_patient_data_entry",
        indexes = {
                @Index(name = "idx_meta_patient_data_entry_patient_data_entry_id", columnList = "patient_data_entry_id")
        })
@Audited
@Getter
@Setter
public class MetaPatientDataEntryEntity extends BaseEntity{
    // 1:1 mapping to PatientDataEntryEntity
    @ManyToOne
    @JoinColumn(name = "patient_data_entry_id", nullable = false)
    private PatientDataEntryEntity patientDataEntry;

    // Value information, only one of these is set, depending on the type of the schema node
    @Column(name = "value_int")
    private Long valueInt;
    @Column(name = "value_float")
    private Float valueFloat;
    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    // e.g. genomic data would be huge (some gigabytes), so better safe then sorry and use LOB
    @Column(name = "value_string", columnDefinition = "TEXT")
    private String valueString;

    @Column(name = "value_lob")
    private String valueBlob;
        // Large Object, e.g. for files, images, etc.
        // Use b64 encoding
    @Column(name = "value_date")
    private LocalDate valueDate;
    @Column(name = "value_date_time")
    private Instant valueDateTime;

    @ManyToOne
    @JoinColumn(name = "value_type", nullable = false)
    private SchemaNodeEntity schemaNode;
}
