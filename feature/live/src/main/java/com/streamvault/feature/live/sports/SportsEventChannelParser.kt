package com.streamvault.feature.live.sports

import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * APEX TV: many playlists carry live events as one channel per game, with the matchup and start time in
 * the channel name, for example `CA (TSN+ 037) | NBA on TSN: Nets vs. Cavaliers (2026-10-24 19:30:05)`.
 * Those channels have no guide data, so turn them into programmes the sports scan can classify.
 */
internal object SportsEventChannelParser {
    /** Playlist stamps are Eastern time (checked against guide start times for the same games). */
    private val stampZone: ZoneId = ZoneId.of("America/New_York")
    private const val DEFAULT_DURATION_MS = 3L * 60L * 60L * 1000L

    private val stampPattern = Regex("""\((\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?\)\s*$""")
    private val trailingParenthetical = Regex("""\s*\((?:[^()]|\([^()]*\))*\)\s*$""")

    /** Sport hints for followed teams whose matchups often carry no sport word ("USC vs. Wisconsin"). */
    private val teamSportHints = mapOf(
        "los angeles lakers" to "Basketball",
        "las vegas aces" to "Basketball",
        "los angeles dodgers" to "Baseball",
        "los angeles chargers" to "American Football",
        "usc trojans" to "American Football",
    )

    data class EventChannel(val title: String, val startTime: Long, val sportHint: String?)

    fun parse(channelName: String): EventChannel? {
        val trimmed = channelName.trim()
        val stamp = stampPattern.find(trimmed) ?: return null
        val (year, month, day, hour, minute) = stamp.destructured
        val second = stamp.groupValues[6].ifEmpty { "0" }
        val start = runCatching {
            LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
                .atZone(stampZone).toInstant().toEpochMilli()
        }.getOrNull() ?: return null
        var title = trimmed.substring(0, stamp.range.first).substringAfterLast('|').trim()
        // "(SkyCast)", "(Pitt vs. #1 Miami (ESP))": feed labels after the matchup are not part of the title.
        while (true) {
            val stripped = title.replace(trailingParenthetical, "").trim()
            if (stripped == title || stripped.isBlank()) break
            title = stripped
        }
        if (title.isBlank()) return null
        val hint = SportsTextNormalizer.normalize(title)
            .split(" vs | v | at ".toRegex())
            .map(SportsAliasRegistry::normalizeTeamName)
            .firstNotNullOfOrNull { teamSportHints[it] }
        return EventChannel(title, start, hint)
    }

    fun programFor(channel: Channel, lookupKey: String): Program? {
        val event = parse(channel.name) ?: return null
        return Program(
            channelId = lookupKey,
            title = event.title,
            startTime = event.startTime,
            endTime = event.startTime + DEFAULT_DURATION_MS,
            genre = event.sportHint,
            category = "Sports",
            providerId = channel.providerId,
        )
    }
}
