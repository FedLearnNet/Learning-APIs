package de.unihamburg.daibetes.api.store.rating;

import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Setter
@Getter
@Entity
@Table(name = "store_ratings")
public class StoreRatingEntity extends BaseAuthEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "app_id")
    private FederatedAppEntity federatedApp;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "model_version_id")
    private ModelVersionEntity modelVersion;

    @ColumnDefault("0")
    private float rating = 0;

    @Column(name = "review_text", length = 1024)
    private String reviewText;

}
