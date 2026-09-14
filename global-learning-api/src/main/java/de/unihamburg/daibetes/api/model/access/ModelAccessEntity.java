package de.unihamburg.daibetes.api.model.access;

import bio.cosy.feddb.core.api.model.ModelAccess;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.model.ModelEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "model_accesses")
public class ModelAccessEntity extends BaseAuthEntity {

    @Enumerated(EnumType.STRING)
    private ModelAccess access;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "model_id", nullable = false)
    private ModelEntity model;

    //TODO?
    @Column(name = "group_name")
    private String groupName;
}
