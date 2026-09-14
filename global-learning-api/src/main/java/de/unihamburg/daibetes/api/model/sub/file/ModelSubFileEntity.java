package de.unihamburg.daibetes.api.model.sub.file;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Setter
@Getter
@Entity
@Table(name = "model_sub_files")
public class ModelSubFileEntity extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "file_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FileEntity file;

    @ManyToOne
    @JoinColumn(name = "model_sub_id", nullable = false)
    private ModelSubEntity modelSub;


}
