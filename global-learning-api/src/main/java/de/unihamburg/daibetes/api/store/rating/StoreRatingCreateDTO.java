package de.unihamburg.daibetes.api.store.rating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class StoreRatingCreateDTO {

    @Positive(message = "rating must be positive")
    private float rating;

    @NotBlank(message = "Review text is required")
    private String reviewText;
}
