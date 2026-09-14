package bio.cosy.feddb.core.rest;

import de.unihamburg.daibetes.api.store.rating.StoreRatingRatingService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;

//@QuarkusTest
@Transactional
//@TestHTTPEndpoint(StoreRatingRatingService.class)
public class FederatedAppRatingServiceTest {
    /*
    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateAppRating() {
        Long validId = 1L;

        StoreRatingCreateDTO newRating = Instancio.create(StoreRatingCreateDTO.class);

        given()
                .pathParam("id", validId)
                .contentType(ContentType.JSON)
                .body(newRating)
                .when().post("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rating", is(newRating.getRating()))
                .body("reviewText", is(newRating.getReviewText()));
    }

    @Test
    @TestSecurity(user = "keycloak10", roles = "admin")
    public void testCreateAppRatingNew() {
        Long validId = 1L;
        StoreRatingCreateDTO newRating = Instancio.create(StoreRatingCreateDTO.class);

        given()
                .pathParam("id", validId)
                .contentType(ContentType.JSON)
                .body(newRating)
                .when().post("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rating", is(newRating.getRating()))
                .body("reviewText", is(newRating.getReviewText()));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateAppRatingInvalidData() {
        Long validId = 1L;

        StoreRatingCreateDTO invalidRating = new StoreRatingCreateDTO();

        given()
                .pathParam("id", validId)
                .contentType(ContentType.JSON)
                .body(invalidRating)
                .when().post("{id}")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListAppRatings() {
        Long validId = 1L;
        given()
                .pathParam("id", validId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListAppRatingsForInvalidApp() {
        Long inValidId = 8888888L;
        given()
                .pathParam("id", inValidId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(0));
    }

     */

}
