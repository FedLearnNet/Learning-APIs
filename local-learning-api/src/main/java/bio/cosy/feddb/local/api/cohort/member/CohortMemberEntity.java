package bio.cosy.feddb.local.api.cohort.member;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Setter
@Getter
@Entity
@Table(name = "cohort_members",
        indexes = {
                @Index(name = "idx_cohort_members_cohort_id", columnList = "cohort_id")
        })
@Audited
public class CohortMemberEntity extends BaseAuthEntity {

    @Enumerated(EnumType.STRING)
    private CohortMemberTypes type;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;
}
