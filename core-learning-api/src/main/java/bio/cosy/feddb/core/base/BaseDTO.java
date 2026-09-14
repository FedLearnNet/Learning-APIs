package bio.cosy.feddb.core.base;

import bio.cosy.feddb.core.validation.constraints.Zero;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.Date;

@Data
public abstract class BaseDTO {


    @Null(groups = ValidationGroups.Post.class)
    @NotNull(groups = {ValidationGroups.Put.class, ValidationGroups.Patch.class, ValidationGroups.Delete.class})
    @PositiveOrZero(message = "Id has to be positive", groups = {ValidationGroups.Put.class, ValidationGroups.Patch.class, ValidationGroups.Delete.class})
    private Long id;

    @JsonFormat(
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            timezone = "UTC"
    )
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private Date createdAt;

    @JsonFormat(
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            timezone = "UTC"
    )
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private Date updatedAt;

    @Zero(groups = ValidationGroups.Post.class)
    @NotNull(groups = {ValidationGroups.Put.class, ValidationGroups.Patch.class, ValidationGroups.Delete.class})
    @PositiveOrZero(message = "Version has to be positive", groups = {ValidationGroups.Put.class, ValidationGroups.Patch.class, ValidationGroups.Delete.class})
    private Long version = 0L;
}
