package bio.cosy.feddb.local.api.file;

import bio.cosy.feddb.core.base.BaseFileEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "files")
public class FileEntity extends BaseFileEntity {
}
