package com.newsroom.conversation;

import com.newsroom.weather.CurrentWeather;

public class ReplyFormatter {

    public static Reply weather(CurrentWeather weather){
        return new Reply.Text("%s, %s%n%.1f°C, %s%nWind %.1f km/h · humidity %d%%"
                .formatted(
                        weather.placeName(), weather.country(),
                        weather.temperatureCelsius(), label(weather.weatherCode()),
                        weather.windKmh(), weather.humidityPercent()));
    }

    private static String label(int code){
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Partly cloudy";
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
