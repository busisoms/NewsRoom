package com.newsroom.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.conversation.LookupException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fetches current weather from Open-Meteo: geocodes a city name, then reads
 * the current conditions for the resolved coordinates.
 *
 * <p>Every failure surfaces as a {@link LookupException} with a
 * {@link LookupException.Reason}, so the consumer catches one type and
 * switches on the reason. The original exception is always attached as the
 * cause, so the stack trace still reaches the log.
 *
 */
public class WeatherClient {
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Sent to the user when the geocoder has no match. The engine is back at the menu by then. */
    static final String NOT_FOUND_MESSAGE =
            "Sorry, I couldn't find a city by that name. Send any message to see the menu and try again.";

    /** Sent to the user for any transport, status, or parsing failure. */
    static final String UNAVAILABLE_MESSAGE =
            "Sorry, the weather service isn't responding right now. Please try again in a few minutes.";

    private final String geocodingBaseUrl;
    private final String forecastBaseUrl;
    private final Duration timeout;
    private final HttpClient client;

    /**
     * @param geocodingBaseUrl base URL of the Open-Meteo geocoding API
     * @param forecastBaseUrl base URL of the Open-Meteo forecast API
     * @param timeout connect and per-request timeout; tests set this low and
     *                point both URLs at a local stub
     */
    public WeatherClient(String geocodingBaseUrl, String forecastBaseUrl, Duration timeout) {
        this.geocodingBaseUrl = geocodingBaseUrl;
        this.forecastBaseUrl = forecastBaseUrl;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Resolves {@code city} and returns its current conditions.
     *
     * @param city the city name as the user typed it; encoded before use, so
     * spaces and non-ASCII names are safe
     * @return the resolved place and its current conditions
     * @throws LookupException {@code NOT_FOUND} if the geocoder has no match,
     *                         {@code UNAVAILABLE} for any transport, status,
     *                         or parsing failure
     */
    public CurrentWeather current(String city) {
        return forecast(geocode(city));
    }

    private Place geocode(String city) {
        String url = geocodingBaseUrl
                + "?name=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1";
        return parseGeocode(get(url, "geocoding"));
    }

    private CurrentWeather forecast(Place place) {
        String url = forecastBaseUrl
                + "?latitude=" + place.latitude()
                + "&longitude=" + place.longitude()
                + "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m";

        Forecast forecast = parseCurrent(get(url, "forecast"));

        return new CurrentWeather(
                place.name(),
                place.country(),
                forecast.temperatureCelsius(),
                forecast.weatherCode(),
                forecast.windKmh(),
                forecast.humidityPercent());
    }

    /**
     * Performs one GET and returns the body, mapping every failure to
     * {@code UNAVAILABLE}. {@code HttpTimeoutException} and
     * {@code ConnectException} are both {@link IOException}s, so one catch
     * covers them.
     */
    private String get(String url, String label) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw unavailable("Malformed Open-Meteo " + label + " URL", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw unavailable("Open-Meteo " + label + " returned HTTP " + status, null);
            }

            return response.body();

        } catch (IOException e) {
            throw unavailable("Open-Meteo " + label + " request failed: " + e.getMessage(), e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Open-Meteo " + label + " request interrupted", e);
        }
    }

    /**
     * Reads the first geocoding result.
     *
     * <p>Open-Meteo omits the {@code results} key entirely when nothing matches,
     * rather than sending an empty array. Missing and empty are both
     * {@code NOT_FOUND}.
     *
     * @throws LookupException {@code NOT_FOUND} for no match,
     *                         {@code UNAVAILABLE} for malformed JSON or a
     *                         missing name or coordinates on a result that was
     *                         returned; a missing country is allowed
     */
    static Place parseGeocode(String json) {
        JsonNode results = readTree(json, "geocoding").path("results");

        if (!results.isArray() || results.isEmpty()) {
            throw new LookupException(LookupException.Reason.NOT_FOUND,
                    NOT_FOUND_MESSAGE, "Geocoding returned no results");
        }

        JsonNode first = results.get(0);

        return new Place(
                requireText(first, "name", "geocoding"),
                first.path("country").textValue(),
                requireDouble(first, "latitude", "geocoding"),
                requireDouble(first, "longitude", "geocoding"));
    }

    /**
     * Reads the numbers out of a forecast response's {@code current} object.
     *
     * @throws LookupException {@code UNAVAILABLE} for malformed JSON or a
     *                         missing field
     */
    static Forecast parseCurrent(String json) {
        JsonNode current = readTree(json, "forecast").path("current");

        if (!current.isObject()) {
            throw unavailable("Forecast response missing current object", null);
        }

        return new Forecast(
                requireDouble(current, "temperature_2m", "forecast"),
                requireInt(current, "weather_code", "forecast"),
                requireDouble(current, "wind_speed_10m", "forecast"),
                requireInt(current, "relative_humidity_2m", "forecast"));
    }

    private static JsonNode readTree(String json, String label) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw unavailable("Malformed " + label + " JSON", e);
        }
    }

    private static String requireText(JsonNode parent, String field, String label) {
        JsonNode node = parent.path(field);
        if (!node.isTextual()) {
            throw unavailable(label + " response missing " + field, null);
        }
        return node.textValue();
    }

    private static double requireDouble(JsonNode parent, String field, String label) {
        JsonNode node = parent.path(field);
        if (!node.isNumber()) {
            throw unavailable(label + " response missing " + field, null);
        }
        return node.asDouble();
    }

    private static int requireInt(JsonNode parent, String field, String label) {
        JsonNode node = parent.path(field);
        if (!node.isNumber()) {
            throw unavailable(label + " response missing " + field, null);
        }
        return node.asInt();
    }

    private static LookupException unavailable(String detail, Throwable cause) {
        return new LookupException(LookupException.Reason.UNAVAILABLE,
                UNAVAILABLE_MESSAGE, detail, cause);
    }

    /**
     * A geocoded place.
     *
     * @param name the geocoder's canonical name
     * @param country the country the place is in, or {@code null} if the geocoder omits it
     * @param latitude the place's latitude
     * @param longitude the place's longitude
     */
    record Place(
            String name, String country,
            double latitude, double longitude
    ) {}

    /**
     * The numbers from a forecast response, before they're paired with the place.
     */
    record Forecast(
            double temperatureCelsius, int weatherCode,
            double windKmh, int humidityPercent
    ) {}
}