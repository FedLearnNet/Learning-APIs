package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorEntity;
import de.unihamburg.daibetes.api.app.tag.FederatedAppTagEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.store.rating.StoreRatingEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.Set;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "federated_apps")
public class FederatedAppEntity extends BaseEntity {

    @Column(columnDefinition = "uuid", nullable = false, updatable = false, unique = true, name = "unique_app_id")
    private UUID uniqueAppId;

    @Column(length = 100)
    private String name;

    @Column(unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    private FederatedAppType type;

    @ColumnDefault("false")
    @Column(name = "supports_federated_learning", nullable = false)
    private boolean supportsFederatedLearning = false;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    private String icon;

    @Column(name = "old_fc_version", nullable = true)
    private Boolean oldFCVersion;

    @ManyToMany
    @JoinTable(
            name = "federated_apps_tags",
            joinColumns = {@JoinColumn(name = "federated_tag_id")},
            inverseJoinColumns = {@JoinColumn(name = "federated_app_id")}
    )
    private Set<FederatedAppTagEntity> tags;

    @OneToMany(mappedBy = "federatedApp", cascade = CascadeType.ALL)
    private Set<FederatedAppAuthorEntity> authors;

    @OneToMany(mappedBy = "federatedApp", cascade = CascadeType.ALL)
    private Set<StoreRatingEntity> ratings;

    @OneToMany(mappedBy = "federatedApp", cascade = CascadeType.ALL)
    @OrderBy("majorVersion DESC, minorVersion DESC, patchVersion DESC")
    private Set<FederatedAppVersionEntity> versions;


    @PrePersist
    protected void onCreate() {
        if (this.uniqueAppId == null) {
            this.uniqueAppId = UUID.randomUUID();
        }
    }

}
