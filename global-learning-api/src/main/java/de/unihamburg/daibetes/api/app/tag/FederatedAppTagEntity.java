package de.unihamburg.daibetes.api.app.tag;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "federated_app_tags")
public class FederatedAppTagEntity extends BaseEntity {

    @Column(unique = true, length = 50)
    private String name;

    @ColumnDefault("false")
    private boolean privacy = false;

    @ManyToMany(mappedBy = "tags")
    private Set<FederatedAppEntity> apps = new HashSet<>();

}
