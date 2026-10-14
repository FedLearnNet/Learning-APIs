package de.unihamburg.daibetes.security;

import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.Vertx;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for every {@link ToolAuthenticated} endpoint of this module: it must carry
 * {@code @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)}, have a rule in
 * {@link ToolEndpointSecurityPolicy}, and be unreachable for a Keycloak admin.
 */
@QuarkusTest
class ToolAuthenticatedEndpointsTest {

    /** Endpoints known when this test was written; guards against a scan that silently finds nothing. */
    private static final int MIN_EXPECTED_ENDPOINTS = 7;

    private static List<ToolEndpoint> endpoints;

    @Inject
    Vertx vertx;

    @TestHTTPResource("/")
    URL baseUrl;

    @BeforeAll
    static void discoverEndpoints() throws IOException, ClassNotFoundException {
        endpoints = scan();
        assertTrue(endpoints.size() >= MIN_EXPECTED_ENDPOINTS,
                "Expected at least " + MIN_EXPECTED_ENDPOINTS + " @ToolAuthenticated endpoints, found " + endpoints);
    }

    @Test
    void everyToolEndpointRequiresToolRole() {
        List<String> failures = endpoints.stream()
                .filter(e -> !requiresToolRole(e.element()) && !requiresToolRole(e.declaringClass()))
                .map(e -> e + " is missing @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)")
                .toList();
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everyToolEndpointHasAPolicyRule() {
        List<String> failures = new ArrayList<>();
        for (ToolEndpoint e : endpoints) {
            var grant = ToolEndpointSecurityPolicy.match(e.httpMethod(), e.concretePath(),
                    e.websocket() ? "websocket" : null);
            if (grant.isEmpty()) {
                failures.add(e + " has no rule in ToolEndpointSecurityPolicy");
            } else if (!Arrays.asList(e.scopes()).contains(grant.get().scope())) {
                failures.add(e + " matches a rule for " + grant.get().scope() + ", which is not in @ToolAuthenticated");
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    void keycloakAdminCannotCallToolEndpoints() throws Exception {
        List<String> failures = new ArrayList<>();
        for (ToolEndpoint e : endpoints) {
            int status = e.websocket() ? websocketUpgradeStatus(e.concretePath()) : restStatus(e);
            if (status != 403) {
                failures.add(e + " returned " + status + " for a Keycloak admin, expected 403");
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private int restStatus(ToolEndpoint e) {
        var request = given();
        if (consumes(e, MediaType.MULTIPART_FORM_DATA)) {
            request = request.multiPart("file", "file.txt", new byte[0]);
        } else {
            request = request.contentType(MediaType.APPLICATION_JSON).body("{}");
        }
        return request.when().request(e.httpMethod(), e.concretePath()).statusCode();
    }

    private int websocketUpgradeStatus(String path) throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                    .setHost(baseUrl.getHost())
                    .setPort(baseUrl.getPort())
                    .setURI(path);
            var socket = client.connect(options).toCompletionStage().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            socket.close();
            return 101;
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof UpgradeRejectedException rejected) {
                return rejected.getStatus();
            }
            throw ex;
        } finally {
            client.close();
        }
    }

    private static boolean consumes(ToolEndpoint e, String mediaType) {
        Consumes consumes = e.element().getAnnotation(Consumes.class);
        if (consumes == null) {
            consumes = e.declaringClass().getAnnotation(Consumes.class);
        }
        return consumes != null && Arrays.asList(consumes.value()).contains(mediaType);
    }

    private static boolean requiresToolRole(AnnotatedElement element) {
        RolesAllowed rolesAllowed = element.getAnnotation(RolesAllowed.class);
        return rolesAllowed != null
                && Arrays.asList(rolesAllowed.value()).contains(ToolApiKeyAuthenticationMechanism.ROLE);
    }

    private static List<ToolEndpoint> scan() throws IOException, ClassNotFoundException {
        // Index the compiled classes directly, so the test never depends on a stale jandex.idx
        java.nio.file.Path classes = Paths.get("target", "classes");
        assertTrue(Files.isDirectory(classes), "Compiled classes not found at " + classes.toAbsolutePath());
        Indexer indexer = new Indexer();
        try (Stream<java.nio.file.Path> files = Files.walk(classes)) {
            for (java.nio.file.Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    indexer.index(in);
                }
            }
        }
        Index index = indexer.complete();

        List<ToolEndpoint> result = new ArrayList<>();
        for (AnnotationInstance instance : index.getAnnotations(DotName.createSimple(ToolAuthenticated.class))) {
            if (instance.target().kind() == AnnotationTarget.Kind.METHOD) {
                result.add(restEndpoint(instance.target().asMethod()));
            } else if (instance.target().kind() == AnnotationTarget.Kind.CLASS) {
                result.add(websocketEndpoint(load(instance.target().asClass().name().toString())));
            }
        }
        return result;
    }

    private static ToolEndpoint restEndpoint(MethodInfo methodInfo) throws ClassNotFoundException {
        Class<?> declaringClass = load(methodInfo.declaringClass().name().toString());
        Method method = Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodInfo.name())
                        && m.getParameterCount() == methodInfo.parametersCount()
                        && m.isAnnotationPresent(ToolAuthenticated.class))
                .findFirst()
                .orElseThrow();
        String httpMethod = Arrays.stream(method.getAnnotations())
                .map(a -> a.annotationType().getAnnotation(HttpMethod.class))
                .filter(java.util.Objects::nonNull)
                .map(HttpMethod::value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(method + " has no HTTP method annotation"));
        Path classPath = declaringClass.getAnnotation(Path.class);
        Path methodPath = method.getAnnotation(Path.class);
        String path = joinPaths(classPath == null ? "" : classPath.value(), methodPath == null ? "" : methodPath.value());
        return new ToolEndpoint(declaringClass.getSimpleName() + "#" + method.getName(), method, declaringClass,
                httpMethod, path, false, method.getAnnotation(ToolAuthenticated.class).value());
    }

    private static ToolEndpoint websocketEndpoint(Class<?> endpointClass) {
        WebSocket webSocket = endpointClass.getAnnotation(WebSocket.class);
        if (webSocket == null) {
            throw new IllegalStateException(endpointClass + " has @ToolAuthenticated but is neither a REST method nor a @WebSocket");
        }
        return new ToolEndpoint(endpointClass.getSimpleName(), endpointClass, endpointClass,
                "GET", webSocket.path(), true, endpointClass.getAnnotation(ToolAuthenticated.class).value());
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }

    private static String joinPaths(String first, String second) {
        String joined = "/" + strip(first) + "/" + strip(second);
        return joined.replaceAll("/+", "/").replaceAll("(.)/$", "$1");
    }

    private static String strip(String path) {
        return path.replaceAll("^/+|/+$", "");
    }

    private record ToolEndpoint(String name, AnnotatedElement element, Class<?> declaringClass,
                                String httpMethod, String pathTemplate, boolean websocket, Scope[] scopes) {

        /** Fills the path template with values the policy rules accept. */
        String concretePath() {
            return pathTemplate
                    .replace("{type}", "app")
                    .replace("{mode}", "prediction")
                    .replaceAll("\\{[^}]+}", "42");
        }

        @Override
        public String toString() {
            return name + " (" + httpMethod + " " + pathTemplate + ")";
        }
    }
}
