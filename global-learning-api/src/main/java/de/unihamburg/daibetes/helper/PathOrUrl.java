package de.unihamburg.daibetes.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PathOrUrl {
    private Path path = null;
    private URL url = null;
    private boolean externalUrl = false;

    public PathOrUrl(Path p) {
        this.path = p;
    }

    public PathOrUrl(URL u) {
        this.url = u;
    }

    public PathOrUrl(URL u, boolean externalUrl) {
        this.url = u;
        this.externalUrl = externalUrl;
    }

    public static PathOrUrl ofUrl(URL u) {
        return new PathOrUrl(u);
    }

    public static PathOrUrl ofExternalUrl(URL u) {
        return new PathOrUrl(u, true);
    }

    public static PathOrUrl ofPath(Path p) {
        return new PathOrUrl(p);
    }

    public String sortKey() {
        if (path != null) return "path:" + path.toString();
        return "url:" + url.toString();
    }

    public JsonNode readTree(ObjectMapper om) throws IOException {
        try {
            if (externalUrl) {
                return om.readTree(url.openStream());
            }
            if (path != null) {
                return om.readTree(path.toFile());
            }
            return om.readTree(url.openStream());
        } catch (Exception e) {
            if (externalUrl) {
                return readTree(om, url.toString());
            }
            if (path != null) {
                return readTree(om, path.toString());
            }
            return om.readTree(url.openStream());
        }
    }


    public JsonNode readTree(ObjectMapper mapper, String spec) throws IOException {
        Log.infof("Reading JSON from %s", spec);
        String cp = spec.startsWith("/") ? spec.substring(1) : spec;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL res = cl.getResource(cp);
        if (res != null) {
            try (InputStream in = res.openStream()) {
                return mapper.readTree(in);
            }
        }

        try {
            URI uri = URI.create(spec);
            if (uri.getScheme() != null) {
                try (InputStream in = uri.toURL().openStream()) {
                    return mapper.readTree(in);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        try (InputStream in = Files.newInputStream(Path.of(spec))) {
            return mapper.readTree(in);
        }
    }

    @Override
    public String toString() {
        return path != null ? path.toString() : String.valueOf(url);
    }
}
