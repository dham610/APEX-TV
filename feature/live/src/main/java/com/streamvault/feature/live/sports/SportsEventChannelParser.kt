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

    /** Numbered game channels such as "NCAAF38: 7:30 PM USC vs Wisconsin" carry a time of day, not a date. */
    private val timeOfDayPattern = Regex(
        """^\s*(NCAAF|NCAAB|WNBA|NBA|NFL|MLB|NHL|MLS)\s*\d+\s*[:|]\s*(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m\.?\s+(.+?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val leagueLabels = mapOf(
        "NCAAF" to "College Football",
        "NCAAB" to "College Basketball",
    )

    data class EventChannel(val title: String, val startTime: Long, val sportHint: String?)

    fun parse(channelName: String, nowMillis: Long = System.currentTimeMillis()): EventChannel? =
        parseDated(channelName) ?: parseTimeOfDay(channelName, nowMillis)

    private fun teamHint(title: String): String? = SportsTextNormalizer.normalize(title)
        .split(" vs | v | at ".toRegex())
        .map(SportsAliasRegistry::normalizeTeamName)
        .firstNotNullOfOrNull { teamSportHints[it] }

    private fun parseTimeOfDay(channelName: String, nowMillis: Long): EventChannel? {
        val match = timeOfDayPattern.find(channelName.trim()) ?: return null
        val (league, hourText, minuteText, meridiem, rest) = match.destructured
        var hour = hourText.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val minute = minuteText.ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val pm = meridiem.equals("p", ignoreCase = true)
        hour = (hour % 12) + if (pm) 12 else 0
        val matchup = rest.replace(trailingParenthetical, "").replace(Regex("""#\d+\s*"""), "").trim()
        if (matchup.isBlank()) return null
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(stampZone).toLocalDate()
        val start = today.atTime(hour, minute).atZone(stampZone).toInstant().toEpochMilli()
        val label = leagueLabels[league.uppercase()] ?: league.uppercase()
        val title = "$label: $matchup"
        return EventChannel(title, start, teamHint(matchup))
    }

    private fun parseDated(channelName: String): EventChannel? {
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
        // Rankings in front of a team name ('#1 Miami') are not part of the name.
        title = title.replace(Regex("""#\d+\s*"""), "").trim()
        if (title.isBlank()) return null
        return EventChannel(title, start, teamHint(title))
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
