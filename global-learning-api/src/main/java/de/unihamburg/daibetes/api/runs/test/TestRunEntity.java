package de.unihamburg.daibetes.api.runs.test;

import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import de.unihamburg.daibetes.api.runs.test.message.TestRunMessageEntity;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "test_runs")
public class TestRunEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(columnDefinition="TEXT")
    private String error;

    @Column(columnDefinition="TEXT")
    private String input;

    @Column(name = "input_file_path")
    private String inputFilePath;

    @Column(columnDefinition="TEXT")
    private String output;

    @Column(columnDefinition="TEXT")
    private String hyperParams;

    // Run metadata (timings + reserved fields) as JSON metadata, nullable for historical runs.
    // Extensible without a schema change. RUNTIME is always stored; OVERHEAD_* only when
    // posymed.runtime.overhead.enabled is true.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL)
    private Set<TestRunMessageEntity> messages;


}
