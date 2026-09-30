package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.exception.UnsafeUrlException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redirect handling against a real local HTTP server. The real guard would refuse
 * 127.0.0.1 outright, so these tests swap in one that allows it and instead treats any
 * path under /internal as the forbidden destination.
 */
class UrlExistenceValidatorRedirectTest {

    private HttpServer server;
    private final List<String> requested = new CopyOnWriteArrayList<>();
    private String base;

    private final UrlExistenceValidator validator = new UrlExistenceValidator(new DestinationGuard() {
        @Override
        public void check(URI uri) {
            if (uri.getPath().startsWith("/internal")) {
                throw new UnsafeUrlException(PRIVATE_ADDRESS);
            }
        }
    });

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requested.add(path);
            switch (path) {
                case "/to-internal" -> redirect(exchange, "/internal/metadata");
                case "/hop1" -> redirect(exchange, "/hop2");
                case "/hop2" -> redirect(exchange, base + "/ok");
                case "/loop" -> redirect(exchange, "/loop");
                case "/ok" -> respond(exchange, 200);
                default -> respond(exchange, 404);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aRedirectToAForbiddenPlaceIsRefusedBeforeItIsRequested() {
        assertThatThrownBy(() -> validator.isUrlExists(base + "/to-internal"))
                .isInstanceOf(UnsafeUrlException.class);
        assertThat(requested).containsExactly("/to-internal");
    }

    @Test
    void ordinaryRedirectsAreFollowedToTheEnd() {
        assertThat(validator.isUrlExists(base + "/hop1")).isTrue();
        assertThat(requested).containsExactly("/hop1", "/hop2", "/ok");
    }

    @Test
    void aRedirectLoopGivesUpAsUnreachable() {
        assertThat(validator.isUrlExists(base + "/loop")).isFalse();
        assertThat(requested).hasSizeLessThanOrEqualTo(12);   // bounded, HEAD then GET at most
    }

    @Test
    void aMissingPageIsStillRejected() {
        assertThat(validator.isUrlExists(base + "/nope")).isFalse();
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange exchange, String location)
            throws java.io.IOException {
        exchange.getResponseHeaders().add("Location", location);
        respond(exchange, 302);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status)
            throws java.io.IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
