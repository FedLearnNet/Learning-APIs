package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.helper.ResourceLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;


@ApplicationScoped
public class ConnectorRemoteHelper {

    @Inject
    ObjectMapper objectMapper;

    public String loadRemoteResource(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new NotFoundException("Remote URL is empty");
        }
        try {
            return ResourceLoader.loadAsString(remoteUrl);
        } catch (IOException e) {
            throw new NotFoundException("Unable to load remote resource: " + e.getMessage(), e);
        }
    }

    public JsonNode parseJsonObject(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);

            if (root == null || !root.isObject()) {
                throw new BadRequestException("Expected a JSON object");
            }

            return root;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid JSON: " + e.getMessage(), e);
        }
    }
}
