package com.newsroom.sports;

import java.util.List;

/**
 * A competition's matches around today, split into what's still to
 * come and what's already been played. Each list holds at most 5 matches.
 *
 * @param competitionName the competition's full name, e.g. {@code "UEFA Champions League"}
 * @param fixtures upcoming matches (status {@code TIMED} or {@code SCHEDULED}), the next games, earliest first
 * @param results played matches (status {@code FINISHED}), the latest games, most recent first
 */
public record SportsResult(
        String competitionName,
        List<Match> fixtures,
        List<Match> results) {
}
