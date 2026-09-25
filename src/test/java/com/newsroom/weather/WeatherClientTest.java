package com.newsroom.weather;

import com.newsroom.conversation.LookupException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherClientTest {
    // Generous for stub tests: the JVM's first HTTP request can take several hundred ms to warm up
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // Only for tests that are meant to hit the timeout
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- parsing, from fixtures captured off the real API ---

    @Test
    void geocodeFoundReadsNameCountryAndCoordinates() {
        WeatherClient.Place place = WeatherClient.parseGeocode(fixture("geocode_found.json"));

        assertEquals(new WeatherClient.Place("Cape Town", "South Africa", -33.92584, 18.42322), place);
    }

    @Test
    void geocodeWithoutCountryStillResolves() {
        WeatherClient.Place place = WeatherClient.parseGeocode(fixture("geocode_no_country.json"));

        assertEquals(new WeatherClient.Place("Nowhere Atoll", null, -10.5, 150.25), place);
    }

    @Test
    void geocodeWithNoResultsKeyIsNotFound() {
        String body = fixture("geocode_empty.json");

        LookupException e = assertThrows(LookupException.class, () -> WeatherClient.parseGeocode(body));

        assertEquals(LookupException.Reason.NOT_FOUND, e.reason(), e.getMessage());
    }

    @Test
    void geocodeWithEmptyResultsArrayIsNotFound() {
        LookupException e = assertThrows(LookupException.class,
                () -> WeatherClient.parseGeocode("{\"results\":[]}"));

        assertEquals(LookupException.Reason.NOT_FOUND, e.reason(), e.getMessage());
    }

    @Test
    void malformedGeocodeIsUnavailable() {
        String body = fixture("malformed.json");

        LookupException e = assertThrows(LookupException.class, () -> WeatherClient.parseGeocode(body));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void forecastReadsCurrentConditions() {
        WeatherClient.Forecast forecast = WeatherClient.parseCurrent(fixture("forecast_current.json"));

        assertEquals(new WeatherClient.Forecast(16.7, 3, 15.1, 87), forecast);
    }

    @Test
    void forecastMissingFieldsIsUnavailable() {
        String body = fixture("forecast_missing_fields.json");

        LookupException e = assertThrows(LookupException.class, () -> WeatherClient.parseCurrent(body));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void forecastWithoutCurrentObjectIsUnavailable() {
        LookupException e = assertThrows(LookupException.class,
                () -> WeatherClient.parseCurrent("{\"latitude\":1.0}"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void malformedForecastIsUnavailable() {
        String body = fixture("malformed.json");

        LookupException e = assertThrows(LookupException.class, () -> WeatherClient.parseCurrent(body));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    // --- HTTP, against a local stub server ---

    @Test
    void currentChainsGeocodeThenForecast() throws IOException {
        AtomicReference<String> geocodeQuery = new AtomicReference<>();
        startServer();
        server.createContext("/geocode", exchange -> {
            geocodeQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, fixture("geocode_found.json"));
        });
        server.createContext("/forecast", exchange ->
                respond(exchange, 200, fixture("forecast_current.json")));

        CurrentWeather weather = client("/geocode", "/forecast").current("Cape Town");

        assertEquals(new CurrentWeather("Cape Town", "South Africa", 16.7, 3, 15.1, 87), weather);
        assertTrue(geocodeQuery.get().contains("name=Cape+Town"), geocodeQuery.get());
    }

    @Test
    void unknownCityOverHttpIsNotFound() throws IOException {
        startServer();
        server.createContext("/geocode", exchange ->
                respond(exchange, 200, fixture("geocode_empty.json")));

        LookupException e = assertThrows(LookupException.class,
                () -> client("/geocode", "/forecast").current("Nowhereville123"));

        assertEquals(LookupException.Reason.NOT_FOUND, e.reason(), e.getMessage());
        assertEquals(WeatherClient.NOT_FOUND_MESSAGE, e.fallbackMessage());
    }

    @Test
    void serverErrorIsUnavailable() throws IOException {
        startServer();
        server.createContext("/geocode", exchange -> respond(exchange, 500, "{}"));

        LookupException e = assertThrows(LookupException.class,
                () -> client("/geocode", "/forecast").current("Cape Town"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void forecastFailureAfterGoodGeocodeIsUnavailable() throws IOException {
        startServer();
        server.createContext("/geocode", exchange ->
                respond(exchange, 200, fixture("geocode_found.json")));
        server.createContext("/forecast", exchange -> respond(exchange, 503, "{}"));

        LookupException e = assertThrows(LookupException.class,
                () -> client("/geocode", "/forecast").current("Cape Town"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    @Test
    void slowServerTimesOutAsUnavailable() throws IOException {
        startServer();
        server.createContext("/geocode", exchange -> {
            try {
                Thread.sleep(SHORT_TIMEOUT.toMillis() * 5);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, fixture("geocode_found.json"));
        });

        long start = System.nanoTime();
        LookupException e = assertThrows(LookupException.class,
                () -> client("/geocode", "/forecast", SHORT_TIMEOUT).current("Cape Town"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
        assertTrue(elapsedMs < SHORT_TIMEOUT.toMillis() * 4, "gave up after " + elapsedMs + "ms");
    }

    @Test
    void unreachableHostIsUnavailable() {
        WeatherClient client = new WeatherClient(
                "http://localhost:1/geocode", "http://localhost:1/forecast", SHORT_TIMEOUT);

        LookupException e = assertThrows(LookupException.class, () -> client.current("Cape Town"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
        assertEquals(WeatherClient.UNAVAILABLE_MESSAGE, e.fallbackMessage());
    }

    @Test
    void technicalDetailGoesToTheLogNotTheUser() {
        String body = fixture("forecast_missing_fields.json");

        LookupException e = assertThrows(LookupException.class, () -> WeatherClient.parseCurrent(body));

        assertTrue(e.getMessage().contains("temperature_2m"), e.getMessage());
        assertFalse(e.fallbackMessage().contains("temperature_2m"), e.fallbackMessage());
    }

    @Test
    void badBaseUrlIsUnavailable() {
        WeatherClient client = new WeatherClient("not a url", "not a url", TIMEOUT);

        LookupException e = assertThrows(LookupException.class, () -> client.current("Cape Town"));

        assertEquals(LookupException.Reason.UNAVAILABLE, e.reason(), e.getMessage());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    private WeatherClient client(String geocodePath, String forecastPath) {
        return client(geocodePath, forecastPath, TIMEOUT);
    }

    private WeatherClient client(String geocodePath, String forecastPath, Duration timeout) {
        String base = "http://localhost:" + server.getAddress().getPort();
        return new WeatherClient(base + geocodePath, base + forecastPath, timeout);
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
        try (InputStream in = WeatherClientTest.class.getResourceAsStream("/weather/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read test fixture: " + name, e);
        }
    }
}
