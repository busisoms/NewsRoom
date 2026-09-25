package com.newsroom.sports;

import com.newsroom.conversation.LookupException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SportsClientTest {
    // Generous for stub tests: the JVM's first HTTP request can take several hundred ms to warm up
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // Only for tests that are meant to hit the timeout
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    // A fixed Friday: the window is 14 days either side, 2026-09-11 to 2026-10-09.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- parsing, from fixtures captured off the real API ---

    @Test
    void successSplitsIntoFixturesAndResultsInOrder() {
        SportsResult result = SportsClient.parse(fixture("matches_success.json"));

        assertEquals("UEFA Champions League", result.competitionName());
        assertEquals(2, result.results().size());
        assertEquals(3, result.fixtures().size());

        Match firstResult = result.results().get(0);
        assertEquals(new Match("Club Brugge", "Aston Villa",
                Instant.parse("2026-09-08T16:45:00Z"), 2, 3), firstResult);

        Match firstFixture = result.fixtures().get(0);
        assertEquals(new Match("RC Lens", "Sporting CP",
                Instant.parse("2026-10-13T16:45:00Z"), null, null), firstFixture);
    }

    @Test
    void moreThanFiveOfEitherKindIsCappedAtFive() {
        SportsResult result = SportsClient.parse(fixture("matches_over_five.json"));

        assertEquals(5, result.results().size());
        assertEquals(5, result.fixtures().size());
    }

    @Test
    void keepsTheNextFixturesAndTheLatestResultsWhateverTheApiOrder() {
        SportsResult result = SportsClient.parse(fixture("matches_unordered.json"));

        assertEquals(5, result.results().size());
        assertEquals("Man United", result.results().get(0).homeTeam());
        assertEquals("PSV", result.results().get(1).homeTeam());
        assertEquals("Liverpool", result.results().get(2).homeTeam());
        assertEquals("Barça", result.results().get(3).homeTeam());
        assertTrue(result.results().stream().noneMatch(m -> m.homeTeam().equals("Club Brugge")),
                "the oldest result should be the one cut");

        assertEquals(5, result.fixtures().size());
        assertEquals("RC Lens", result.fixtures().get(0).homeTeam());
        assertTrue(result.fixtures().stream().noneMatch(m -> m.utcDate().isAfter(Instant.parse("2026-10-14T23:59:59Z"))),
                "the 20 Oct fixtures are further out than the next five");
    }

    @Test
    void postponedAndCancelledMatchesAreDropped() {
        SportsResult result = SportsClient.parse(fixture("matches_dropped_statuses.json"));

        assertEquals(1, result.results().size());
        assertTrue(result.fixtures().isEmpty());
        assertEquals("Porto", result.results().get(0).homeTeam());
    }

    @Test
    void emptyMatchesArrayIsNotFound() {
        String body = fixture("matches_empty.json");

        LookupException e = assertThrows(LookupException.class, () -> SportsClient.parse(body));

        assertEquals(LookupException.Reason.NOT_FOUND, e.reason(), e.getMessage());
        assertEquals(SportsClient.NOT_FOUND_MESSAGE, e.fallbackMessage());
    }

    @Test
    void malformedJsonIsUnavailable() {
        String body = fixture("malformed.json");

        LookupException e = assertThrows(LookupException.class, () -> SportsClient.parse(body));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void finishedMatchMissingScoreIsUnavailable() {
        String body = fixture("matches_missing_score.json");

        LookupException e = assertThrows(LookupException.class, () -> SportsClient.parse(body));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    // --- HTTP, against a local stub server ---

    @Test
    void matchesRequestsTheWindowAroundTodayWithAuthHeader() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        startServer();
        server.createContext("/competitions/CL/matches", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            authHeader.set(exchange.getRequestHeaders().getFirst("X-Auth-Token"));
            respond(exchange, 200, fixture("matches_success.json"));
        });

        SportsResult result = client().matches("CL");

        assertEquals(2, result.results().size());
        assertEquals("dateFrom=2026-09-11&dateTo=2026-10-09", query.get());
        assertEquals("test-api-key", authHeader.get());
    }

    @Test
    void noMatchesInTheWindowOverHttpIsNotFound() throws IOException {
        startServer();
        server.createContext("/competitions/PL/matches", exchange ->
                respond(exchange, 200, fixture("matches_empty.json")));

        LookupException e = assertThrows(LookupException.class, () -> client().matches("PL"));

        assertEquals(LookupException.Reason.NOT_FOUND, e.reason(), e.getMessage());
    }

    @Test
    void serverErrorIsUnavailable() throws IOException {
        startServer();
        server.createContext("/competitions/PL/matches", exchange -> respond(exchange, 500, "{}"));

        LookupException e = assertThrows(LookupException.class, () -> client().matches("PL"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void slowServerTimesOutAsUnavailable() throws IOException {
        startServer();
        server.createContext("/competitions/PL/matches", exchange -> {
            try {
                Thread.sleep(SHORT_TIMEOUT.toMillis() * 5);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, fixture("matches_success.json"));
        });

        long start = System.nanoTime();
        LookupException e = assertThrows(LookupException.class,
                () -> client(SHORT_TIMEOUT).matches("PL"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
        assertTrue(elapsedMs < SHORT_TIMEOUT.toMillis() * 4, "gave up after " + elapsedMs + "ms");
    }

    @Test
    void unreachableHostIsUnavailable() {
        SportsClient client = new SportsClient(
                "http://localhost:1", "test-api-key", SHORT_TIMEOUT, FIXED_CLOCK);

        LookupException e = assertThrows(LookupException.class, () -> client.matches("PL"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
        assertEquals(SportsClient.UNAVAILABLE_MESSAGE, e.fallbackMessage());
    }

    @Test
    void badBaseUrlIsUnavailable() {
        SportsClient client = new SportsClient("not a url", "test-api-key", TIMEOUT, FIXED_CLOCK);

        LookupException e = assertThrows(LookupException.class, () -> client.matches("PL"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    private SportsClient client() {
        return client(TIMEOUT);
    }

    private SportsClient client(Duration timeout) {
        String base = "http://localhost:" + server.getAddress().getPort();
        return new SportsClient(base, "test-api-key", timeout, FIXED_CLOCK);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture(String name) {
        try (InputStream in = SportsClientTest.class.getResourceAsStream("/sports/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read test fixture: " + name, e);
        }
    }
}
