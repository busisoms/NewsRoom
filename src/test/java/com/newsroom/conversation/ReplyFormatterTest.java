package com.newsroom.conversation;

import com.newsroom.weather.CurrentWeather;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}
