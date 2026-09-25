package com.newsroom.weather;

/**
 * A resolved current-conditions reading for a city.
 *
 * <p>Carries the place name and country as Open-Meteo resolved them, so
 * {@code "cape town"} comes back as {@code "Cape Town, South Africa"}.
 *
 * @param placeName the geocoder's canonical name for the place
 * @param country the country the place is in, or {@code null} if the geocoder omits it
 * @param temperatureCelsius current temperature in °C
 * @param weatherCode Open-Meteo / WMO weather code
 * @param windKmh current wind speed in km/h
 * @param humidityPercent current relative humidity as a percentage
 */
public record CurrentWeather(
        String placeName,
        String country,
        double temperatureCelsius,
        int weatherCode,
        double windKmh,
        int humidityPercent) {
}