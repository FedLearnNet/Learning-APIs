package de.unihamburg.daibetes.api.app.author;

import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_app_authors")
public class FederatedAppAuthorEntity extends BaseAuthEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_id", nullable = false)
    private FederatedAppEntity federatedApp;

}
