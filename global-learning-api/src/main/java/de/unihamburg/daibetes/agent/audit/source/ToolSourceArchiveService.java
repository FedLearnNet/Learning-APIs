package de.unihamburg.daibetes.agent.audit.source;

import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
public class ToolSourceArchiveService {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final Pattern REF = Pattern.compile("[A-Za-z0-9._/-]{1,100}");

    private static final int MAX_ENTRIES = 5000;

    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "venv", ".venv", "__pycache__",
            ".idea", ".vscode", ".mypy_cache", ".pytest_cache", "site-packages", ".tox", "dist", ".eggs");

    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "pdf", "zip", "gz", "tgz", "tar", "bz2", "xz", "7z", "rar", "jar", "whl", "egg", "so", "dll", "dylib",
            "exe", "bin", "pkl", "pickle", "joblib", "pt", "pth", "h5", "hdf5", "onnx", "npy", "npz", "parquet",
            "feather", "arrow", "xlsx", "xls", "docx", "pptx", "rds", "rdata", "sqlite", "db", "woff", "woff2", "ttf",
            "otf", "eot", "mp3", "mp4", "wav", "avi", "mov", "class", "pyc", "pyo", "ipynb_checkpoints");

    private static final Set<String> DATA_EXTENSIONS = Set.of("csv", "tsv", "vcf", "fasta", "fastq", "fa", "bed",
            "gtf", "gff", "mtx", "h5ad", "loom");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Inject
    FLNetConfig config;

    public Optional<ToolSourceSnapshot> download(String sourceUrl, String ref) throws InterruptedException {
        if (StringUtils.isBlank(sourceUrl) || StringUtils.isBlank(ref) || !REF.matcher(ref).matches()) {
            return Optional.empty();
        }
        for (String archiveUrl : archiveUrls(sourceUrl, ref)) {
            try {
                Optional<byte[]> archive = fetch(archiveUrl);
                if (archive.isPresent()) {
                    return Optional.of(unzip(archiveUrl, archive.get()));
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.warnf("Cannot download source archive %s: %s", archiveUrl, e.getMessage());
            }
        }
        return Optional.empty();
    }

    List<String> archiveUrls(String sourceUrl, String ref) {
        String base = StringUtils.removeEnd(StringUtils.removeEnd(sourceUrl.trim(), "/"), ".git");
        URI uri;
        try {
            uri = URI.create(base);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            return List.of();
        }
        String name = base.substring(base.lastIndexOf('/') + 1);
        String encodedRef = ref.replace("/", "%2F");
        if ("github.com".equalsIgnoreCase(uri.getHost())) {
            return List.of(base + "/archive/" + ref + ".zip");
        }
        return List.of(base + "/-/archive/" + encodedRef + "/" + name + "-" + encodedRef + ".zip",
                base + "/archive/" + ref + ".zip");
    }

    private Optional<byte[]> fetch(String archiveUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(archiveUrl)).timeout(TIMEOUT).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                Log.debugf("Source archive %s answered %d", archiveUrl, response.statusCode());
                return Optional.empty();
            }
            byte[] archive = readLimited(body, config.llmAudit().maxArchiveBytes(), "archive");
            // Private repositories redirect to an HTML sign-in page
            if (archive.length < 4 || archive[0] != 'P' || archive[1] != 'K') {
                Log.debugf("Source archive %s is no zip file", archiveUrl);
                return Optional.empty();
            }
            return Optional.of(archive);
        }
    }

    private ToolSourceSnapshot unzip(String archiveUrl, byte[] archive) throws IOException {
        List<ToolSourceFile> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    skipped.add("... further entries beyond " + MAX_ENTRIES);
                    break;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String path = stripRoot(entry.getName());
                if (path.isEmpty() || isIgnored(path)) {
                    continue;
                }
                String extension = extension(path);
                if (BINARY_EXTENSIONS.contains(extension) || DATA_EXTENSIONS.contains(extension)) {
                    skipped.add(path);
                    continue;
                }
                byte[] content;
                try {
                    content = readLimited(zip, config.llmAudit().maxFileBytes(), path);
                } catch (IOException e) {
                    skipped.add(path + " (too large)");
                    continue;
                }
                if (isBinary(content)) {
                    skipped.add(path);
                    continue;
                }
                files.add(new ToolSourceFile(path, new String(content, StandardCharsets.UTF_8)));
            }
        }
        return new ToolSourceSnapshot(archiveUrl, files, skipped);
    }

    // Archives wrap everything into "<repo>-<ref>/"
    private String stripRoot(String name) {
        int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private boolean isIgnored(String path) {
        for (String segment : path.split("/")) {
            if (IGNORED_DIRS.contains(segment) || segment.endsWith(".egg-info")) {
                return true;
            }
        }
        return false;
    }

    private String extension(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isBinary(byte[] content) {
        int checked = Math.min(content.length, 8000);
        for (int i = 0; i < checked; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private byte[] readLimited(InputStream in, long limit, String what) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException(what + " exceeds " + limit + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
