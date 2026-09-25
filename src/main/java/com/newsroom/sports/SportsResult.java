package com.newsroom.sports;

import java.util.List;

/**
 * A competition's matches for the current week, split into what's still to
 * come and what's already been played. Each list holds at most 5 matches.
 *
 * @param competitionName the competition's full name, e.g. {@code "UEFA Champions League"}
 * @param fixtures upcoming matches (status {@code TIMED} or {@code SCHEDULED}), earliest first
 * @param results played matches (status {@code FINISHED}), as returned by the API
 */
public record SportsResult(
        String competitionName,
        List<Match> fixtures,
        List<Match> results) {
}
