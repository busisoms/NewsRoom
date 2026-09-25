package com.newsroom.sports;

import java.time.Instant;

/**
 * One match in a competition's this-week window.
 *
 * @param homeTeam the home team's short name
 * @param awayTeam the away team's short name
 * @param utcDate kickoff time
 * @param homeScore full-time home score, or {@code null} for a match not yet played
 * @param awayScore full-time away score, or {@code null} for a match not yet played
 */
public record Match(
        String homeTeam,
        String awayTeam,
        Instant utcDate,
        Integer homeScore,
        Integer awayScore) {
}
