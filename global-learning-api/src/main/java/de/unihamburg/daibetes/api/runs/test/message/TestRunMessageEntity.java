package de.unihamburg.daibetes.api.runs.test.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import de.unihamburg.daibetes.api.runs.test.TestRunEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "test_run_messages")
public class TestRunMessageEntity extends BaseEntity {

    private String process;

    @Enumerated(EnumType.STRING)
    private RunMessageTypes type;

    @Column(columnDefinition="TEXT")
    private String message;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRunEntity run;

}
