package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningEntity;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;

@Entity
@Table(name = "learning_request")
@Getter
@Setter
public class LearningRequestEntity extends BaseEntity {

    @Column(name = "platform_user_id", length = 255, nullable = false)
    private String platformUserId;

    @Column(name = "global_request_id", nullable = false)
    private String globalExperimentUniqueId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "channel_id", length = 64)
    private String channelId;

    @Column(name = "model_need_to_be_public", columnDefinition = "boolean default false")
    private Boolean modelNeedToBePublic;

    @Column(name = "model_can_be_public", columnDefinition = "boolean default false")
    private Boolean modelCanBePublic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LearningRequestStatus status = LearningRequestStatus.PENDING;

    @OneToOne(mappedBy = "request", cascade = CascadeType.ALL)
    private LearningProjectEntity project;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<PatientLearningEntity> patients;
}
