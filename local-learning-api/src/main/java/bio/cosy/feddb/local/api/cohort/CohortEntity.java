package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberEntity;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.permission.PermissionEntity;
import bio.cosy.feddb.local.api.cohort.queryability.CohortQueryAbilityEntity;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;


@Entity
@Table(name = "cohort")
@Getter
@Setter
@Audited
public class CohortEntity extends BaseAuthEntity {
    // We make the name unique to avoid confusion in the frontend.
    @Column(name = "name", length = 255, nullable = false, unique = true)
    private String name;
    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "cite_as", columnDefinition = "text")
    private String citeAs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PublicationStatus status;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "purpose", columnDefinition = "text")
    private String purpose;

    @Column(name = "copyright", columnDefinition = "text")
    private String copyright;

    @Column(name = "copyright_label")
    private String copyrightLabel;

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SchemaNodeEntity> schemaNodes;

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<CohortCriterionEntity> criteria = new LinkedHashSet<>();

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<PatientEntity> patients;

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<PermissionEntity> permissions;

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<CohortQueryAbilityEntity> queryabilities;

    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL, orphanRemoval = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @NotAudited
    private Set<ConnectorFilesEntity> connectorFiles;
    
    @OneToMany(mappedBy = "cohort", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<CohortMemberEntity> members;
}
