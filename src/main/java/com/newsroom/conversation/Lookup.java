package com.newsroom.conversation;

/**
 * An external data fetch the conversation engine wants performed,
 * with the reply held back until the result comes in.
 */
public sealed interface Lookup permits Lookup.Weather, Lookup.Sports, Lookup.News {

    /**
     * A weather lookup for a city.
     *
     * @param city the city to fetch weather for
     */
    record Weather(String city) implements Lookup{}

    /**
     * A sports lookup for a competition.
     *
     * @param competitionCode the competition's code, e.g. {@code "PL"}
     */
    record Sports(String competitionCode) implements Lookup{}

    /**
     * A news lookup for a topic.
     *
     * @param topic the topic to fetch news for
     */
    record News(String topic) implements Lookup{}
}
