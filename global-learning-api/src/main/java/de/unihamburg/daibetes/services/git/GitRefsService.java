package de.unihamburg.daibetes.services.git;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class GitRefsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String advertisedRefs(String repositoryUrl) throws IOException, InterruptedException {
        URI refs = URI.create(StringUtils.removeEnd(repositoryUrl, "/") + "/info/refs?service=git-upload-pack");
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(refs).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("refs answered " + response.statusCode());
        }
        return response.body();
    }
}
