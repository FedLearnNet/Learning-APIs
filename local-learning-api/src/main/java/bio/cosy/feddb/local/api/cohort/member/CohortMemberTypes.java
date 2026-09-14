package bio.cosy.feddb.local.api.cohort.member;

import com.fasterxml.jackson.annotation.JsonIgnore;

public enum CohortMemberTypes {
    MAINTAINER;

    @JsonIgnore
    public boolean canEdit() {
        return this.equals(MAINTAINER);
    }

    @JsonIgnore
    public boolean canEditPatients() {
        return this.equals(MAINTAINER);
    }

    @JsonIgnore
    public boolean canDelete() {
        return this.equals(MAINTAINER);
    }
}
