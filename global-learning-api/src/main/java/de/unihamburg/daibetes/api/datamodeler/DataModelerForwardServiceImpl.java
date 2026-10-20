package de.unihamburg.daibetes.api.datamodeler;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class DataModelerForwardServiceImpl implements DataModelerForwardService {

    private static final List<String> RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION);
    // Absolute path of plain segments; no dot segments, no encoded dot, slash or backslash
    private static final Pattern FORWARDED_PATH =
            Pattern.compile("(?!.*%(?i:2e|2f|5c))(/(?!\\.{1,2}(?:/|$))[A-Za-z0-9\\-._~!$&'()*+,;=:@%]*)*");

    @ConfigProperty(name = "quarkus.rest-client.data-modeler-service.url")
    String dataModelerUrl;

    @Inject
    SecurityIdentity identity;

    private final Client client = ClientBuilder.newClient();

    @PreDestroy
    void close() {
        client.close();
    }

    @Override
    public Response get(String path, UriInfo uriInfo, HttpHeaders headers) {
        return forward("GET", uriInfo, headers, null);
    }

    @Override
    public Response post(String path, UriInfo uriInfo, HttpHeaders headers, byte[] body) {
        return forward("POST", uriInfo, headers, body);
    }

    @Override
    public Response put(String path, UriInfo uriInfo, HttpHeaders headers, byte[] body) {
        return forward("PUT", uriInfo, headers, body);
    }

    @Override
    public Response delete(String path, UriInfo uriInfo, HttpHeaders headers) {
        return forward("DELETE", uriInfo, headers, null);
    }

    private Response forward(String method, UriInfo uriInfo, HttpHeaders headers, byte[] body) {
        Invocation.Builder request = client.target(targetUri(uriInfo)).request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .header(HttpHeaders.ACCEPT, headers.getHeaderString(HttpHeaders.ACCEPT));
        MediaType contentType = headers.getMediaType() != null ? headers.getMediaType() : MediaType.APPLICATION_JSON_TYPE;

        try (Response response = body == null
                ? request.method(method)
                : request.method(method, Entity.entity(body, contentType))) {
            Response.ResponseBuilder forwarded = Response.status(response.getStatus())
                    .entity(response.hasEntity() ? response.readEntity(byte[].class) : null);
            RESPONSE_HEADERS.forEach(name -> {
                String value = response.getHeaderString(name);
                if (value != null) {
                    forwarded.header(name, value);
                }
            });
            return forwarded.build();
        }
    }

    private URI targetUri(UriInfo uriInfo) {
        URI requestUri = uriInfo.getRequestUri();
        String rawPath = requestUri.getRawPath();
        String forwardedPath = rawPath.substring(rawPath.indexOf(PATH) + PATH.length());
        if (!FORWARDED_PATH.matcher(forwardedPath).matches()) {
            throw new BadRequestException("Invalid data modeler path");
        }
        String query = requestUri.getRawQuery() != null ? "?" + requestUri.getRawQuery() : "";
        return URI.create(dataModelerUrl.replaceAll("/+$", "") + forwardedPath + query);
    }

    private String accessToken() {
        AccessTokenCredential credential = identity.getCredential(AccessTokenCredential.class);
        if (credential == null) {
            throw new NotAuthorizedException("Bearer");
        }
        return credential.getToken();
    }
}
