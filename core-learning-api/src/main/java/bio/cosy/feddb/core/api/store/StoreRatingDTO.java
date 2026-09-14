package bio.cosy.feddb.core.api.store;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class StoreRatingDTO extends BaseAuthDTO {

    private Long federatedAppId;
    private Long modelVersionId;

    private float rating;

    private String reviewText;
}
