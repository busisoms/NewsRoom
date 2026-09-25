package com.newsroom.conversation;

import com.newsroom.sports.Match;
import com.newsroom.sports.SportsResult;
import com.newsroom.weather.CurrentWeather;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplyFormatterTest {
    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void weatherShowsPlaceConditionsAndNumbers() {
        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Cape Town", "South Africa", 16.7, 3, 15.1, 87));

        assertEquals(new Reply.Text("""
                Cape Town, South Africa
                16.7°C, Overcast
                Wind 15.1 km/h · humidity 87%"""), reply);
    }

    @Test
    void weatherRoundsToOneDecimal() {
        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Cape Town", "South Africa", 16.66, 0, 15.04, 87));

        assertEquals(new Reply.Text("""
                Cape Town, South Africa
                16.7°C, Clear sky
                Wind 15.0 km/h · humidity 87%"""), reply);
    }

    @Test
    void missingCountryIsLeftOut() {
        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Nowhere Atoll", null, 28.0, 2, 10.0, 70));

        assertEquals(new Reply.Text("""
                Nowhere Atoll
                28.0°C, Partly cloudy
                Wind 10.0 km/h · humidity 70%"""), reply);
    }

    @Test
    void unknownWeatherCodeStillFormats() {
        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Cape Town", "South Africa", 16.7, 42, 15.1, 87));

        assertEquals(new Reply.Text("""
                Cape Town, South Africa
                16.7°C, Unknown conditions
                Wind 15.1 km/h · humidity 87%"""), reply);
    }

    @Test
    void negativeTemperatureKeepsItsSign() {
        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Oslo", "Norway", -3.2, 71, 5.0, 90));

        assertEquals(new Reply.Text("""
                Oslo, Norway
                -3.2°C, Snow
                Wind 5.0 km/h · humidity 90%"""), reply);
    }

    @Test
    void decimalSeparatorIgnoresServerLocale() {
        Locale.setDefault(Locale.GERMANY);

        Reply reply = ReplyFormatter.weather(
                new CurrentWeather("Cape Town", "South Africa", 16.7, 3, 15.1, 87));

        assertEquals(new Reply.Text("""
                Cape Town, South Africa
                16.7°C, Overcast
                Wind 15.1 km/h · humidity 87%"""), reply);
    }

    @Test
    void sportsShowsResultsThenFixtures() {
        SportsResult result = new SportsResult("UEFA Champions League",
                List.of(new Match("RC Lens", "Sporting CP", Instant.parse("2026-10-13T16:45:00Z"), null, null)),
                List.of(new Match("Club Brugge", "Aston Villa", Instant.parse("2026-09-08T16:45:00Z"), 2, 3)));

        Reply reply = ReplyFormatter.sports(result);

        assertEquals(new Reply.Text("""
                UEFA Champions League

                Recent results:
                Club Brugge 2-3 Aston Villa

                Upcoming fixtures:
                RC Lens v Sporting CP -> Tue 13 Oct 16:45 UTC"""), reply);
    }

    @Test
    void sportsWithOnlyResultsLeavesOutFixturesSection() {
        SportsResult result = new SportsResult("Premier League",
                List.of(),
                List.of(new Match("Arsenal", "Chelsea", Instant.parse("2026-09-08T16:45:00Z"), 1, 0)));

        Reply reply = ReplyFormatter.sports(result);

        assertEquals(new Reply.Text("""
                Premier League

                Recent results:
                Arsenal 1-0 Chelsea"""), reply);
    }

    @Test
    void sportsWithOnlyFixturesLeavesOutResultsSection() {
        SportsResult result = new SportsResult("Premier League",
                List.of(new Match("Arsenal", "Chelsea", Instant.parse("2026-09-08T16:45:00Z"), null, null)),
                List.of());

        Reply reply = ReplyFormatter.sports(result);

        assertEquals(new Reply.Text("""
                Premier League

                Upcoming fixtures:
                Arsenal v Chelsea -> Tue 8 Sep 16:45 UTC"""), reply);
    }
}
