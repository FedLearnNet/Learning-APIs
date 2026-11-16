package bio.cosy.feddb.core.autoimport;

import bio.cosy.feddb.core.helper.ResourceScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AutoImportPathHandlerTest {
    @TempDir
    Path temp;

    private AutoImportPathHandler handler() {
        AutoImportPathHandler handler = new AutoImportPathHandler();
        handler.objectMapper = new ObjectMapper();
        handler.resourceScanner = new ResourceScanner();
        handler.init();
        return handler;
    }

    @Test
    void readsAnAbsoluteJsonFileAndNestedDirectory() throws Exception {
        Path nested = Files.createDirectories(temp.resolve("nested"));
        Path file = Files.writeString(nested.resolve("one.json"), "{\"name\":\"one\"}");
        Files.writeString(nested.resolve("ignore.txt"), "ignored");
        AutoImportPathHandler handler = handler();
        assertEquals(1, handler.resolveSources(file).count());
        assertEquals(1, handler.resolveSources(temp).count());
        assertEquals(List.of(new Entry("one")), handler.readAll("test",
                handler.resolveSources(file).toList(), Entry.class, OnParseError.FAIL));
    }

    @Test
    void readsClasspathFilesWithOrWithoutLeadingSlash() throws Exception {
        Files.writeString(temp.resolve("one.json"), "{\"name\":\"one\"}");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            assertEquals(1, handler().resolveSources(Path.of("one.json")).count());
            assertEquals(1, handler().resolveSources(Path.of("/one.json")).count());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void readsSingleFileInsideJarAfterScannerClosesTheFileSystem() throws Exception {
        Path jar = temp.resolve("imports.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("one.json"));
            out.write("{\"name\":\"one\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            AutoImportPathHandler handler = handler();
            assertEquals(List.of(new Entry("one")), handler.readAll("test",
                    handler.resolveSources(Path.of("one.json")).toList(), Entry.class, OnParseError.FAIL));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void whitelistMatchesBareRelativeFileNames() {
        PathOrUrl source = PathOrUrl.ofPath(Path.of("one.json"));
        assertEquals(List.of(source), PathOrUrl.filterByWhitelist(List.of(source), Optional.of(List.of("one.json"))));
        assertTrue(PathOrUrl.filterByWhitelist(List.of(source), Optional.of(List.of("other.json"))).isEmpty());
    }

    @Test
    void malformedJsonFailsStrictImports() throws Exception {
        Path file = Files.writeString(temp.resolve("bad.json"), "{broken");
        assertThrows(IllegalStateException.class,
                () -> handler().readAll("test", List.of(PathOrUrl.ofPath(file)), Entry.class, OnParseError.FAIL));
    }

    record Entry(String name) { }
}
