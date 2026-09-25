package com.newsroom.conversation;

import com.newsroom.weather.CurrentWeather;

import java.util.Locale;

/**
 * Turns lookup results into the {@link Reply} sent to the user.
 * Stateless; kept short so it reads well on a phone.
 */
public class ReplyFormatter {

    /**
     * Formats current conditions as three lines: place, temperature and
     * conditions, then wind and humidity.
     *
     * <p>Numbers use {@link Locale#ROOT} so the decimal separator is always a
     * dot, whatever locale the server runs in.
     *
     * @param weather the resolved place and its current conditions; the country
     *                may be {@code null}, in which case it's left out
     * @return a text reply
     */
    public static Reply weather(CurrentWeather weather){
        String place = weather.country() == null || weather.country().isBlank()
                ? weather.placeName()
                : weather.placeName() + ", " + weather.country();

        return new Reply.Text(String.format(Locale.ROOT,
                "%s\n%.1f°C, %s\nWind %.1f km/h · humidity %d%%",
                place,
                weather.temperatureCelsius(), label(weather.weatherCode()),
                weather.windKmh(), weather.humidityPercent()));
    }

    /**
     * Maps a WMO weather code, as Open-Meteo reports it, to a short description.
     * Unknown codes get a neutral label rather than failing the reply.
     */
    private static String label(int code){
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown conditions";
        };
    }
}
