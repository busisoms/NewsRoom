package com.newsroom.sports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsroom.conversation.LookupException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches this week's matches for a competition from football-data.org.
 *
 * <p>Every failure surfaces as a {@link LookupException} with a
 * {@link LookupException.Reason}, so the consumer catches one type and
 * switches on the reason. The original exception is always attached as the
 * cause, so the stack trace still reaches the log.
 */
public class SportsClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_PER_LIST = 5;

    /** Sent to the user when the competition has no matches in the current week. */
    static final String NOT_FOUND_MESSAGE =
            "No matches this week for that competition. Send any message to see the menu and try again.";

    /** Sent to the user for any transport, status, or parsing failure. */
    static final String UNAVAILABLE_MESSAGE =
            "Sorry, the football results service isn't responding right now. Please try again in a few minutes.";

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final Clock clock;
    private final HttpClient client;

    /**
     * @param baseUrl base URL of the football-data.org API, e.g. {@code https://api.football-data.org/v4}
     * @param apiKey key sent as the {@code X-Auth-Token} header
     * @param timeout connect and per-request timeout; tests set this low and point the URL at a local stub
     * @param clock supplies "now" when computing the current week's date range; tests pin it
     */
    public SportsClient(String baseUrl, String apiKey, Duration timeout, Clock clock) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.clock = clock;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Fetches this week's matches for a competition, split into fixtures and results.
     *
     * @param competitionCode the competition's code, e.g. {@code "PL"}
     * @return at most 5 fixtures and 5 results
     * @throws LookupException {@code NOT_FOUND} if the competition has no matches this week,
     *                         {@code UNAVAILABLE} for any transport, status, or parsing failure
     */
    public SportsResult matches(String competitionCode) {
        LocalDate weekStart = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        String url = baseUrl + "/competitions/" + competitionCode + "/matches"
                + "?dateFrom=" + weekStart + "&dateTo=" + weekEnd;

        return parse(get(url));
    }

    /**
     * Performs one GET and returns the body, mapping every failure to {@code UNAVAILABLE}.
     * {@code HttpTimeoutException} and {@code ConnectException} are both {@link IOException}s,
     * so one catch covers them.
     */
    private String get(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw unavailable("Malformed football-data.org URL", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("X-Auth-Token", apiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw unavailable("football-data.org returned HTTP " + status, null);
            }

            return response.body();

        } catch (IOException e) {
            throw unavailable("football-data.org request failed: " + e.getMessage(), e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("football-data.org request interrupted", e);
        }
    }

    /**
     * Reads the competition name and splits {@code matches} into fixtures (status
     * {@code TIMED} or {@code SCHEDULED}) and results (status {@code FINISHED}), each
     * capped at 5. Any other status (e.g. {@code POSTPONED}, {@code CANCELLED},
     * {@code SUSPENDED}) is dropped.
     *
     * @throws LookupException {@code NOT_FOUND} if {@code matches} is empty,
     *                         {@code UNAVAILABLE} for malformed JSON or a missing required field
     */
    static SportsResult parse(String json) {
        JsonNode root = readTree(json);
        String competitionName = requireText(root.path("competition"), "name", "competition");

        JsonNode matches = root.path("matches");
        if (!matches.isArray() || matches.isEmpty()) {
            throw new LookupException(LookupException.Reason.NOT_FOUND,
                    NOT_FOUND_MESSAGE, "No matches returned for the current week");
        }

        List<Match> fixtures = new ArrayList<>();
        List<Match> results = new ArrayList<>();

        for (JsonNode match : matches) {
            String status = requireText(match, "status", "match");

            if (status.equals("FINISHED")) {
                if (results.size() < MAX_PER_LIST) {
                    results.add(readMatch(match, true));
                }
            } else if (status.equals("TIMED") || status.equals("SCHEDULED")) {
                if (fixtures.size() < MAX_PER_LIST) {
                    fixtures.add(readMatch(match, false));
                }
            }
        }

        return new SportsResult(competitionName, fixtures, results);
    }

    private static Match readMatch(JsonNode match, boolean withScore) {
        String homeTeam = requireText(match.path("homeTeam"), "shortName", "match");
        String awayTeam = requireText(match.path("awayTeam"), "shortName", "match");
        Instant utcDate = requireInstant(match, "utcDate", "match");

        if (!withScore) {
            return new Match(homeTeam, awayTeam, utcDate, null, null);
        }

        JsonNode fullTime = match.path("score").path("fullTime");
        return new Match(homeTeam, awayTeam, utcDate,
                requireInt(fullTime, "home", "match score"),
                requireInt(fullTime, "away", "match score"));
    }

    private static JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw unavailable("Malformed football-data.org JSON", e);
        }
    }

    private static String requireText(JsonNode parent, String field, String label) {
        JsonNode node = parent.path(field);
        if (!node.isTextual()) {
            throw unavailable(label + " response missing " + field, null);
        }
        return node.textValue();
    }

    private static int requireInt(JsonNode parent, String field, String label) {
        JsonNode node = parent.path(field);
        if (!node.isNumber()) {
            throw unavailable(label + " response missing " + field, null);
        }
        return node.asInt();
    }

    private static Instant requireInstant(JsonNode parent, String field, String label) {
        String text = requireText(parent, field, label);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw unavailable(label + " response has an unparseable " + field, e);
        }
    }

    private static LookupException unavailable(String detail, Throwable cause) {
        return new LookupException(LookupException.Reason.UNAVAILABLE,
                UNAVAILABLE_MESSAGE, detail, cause);
    }
}
