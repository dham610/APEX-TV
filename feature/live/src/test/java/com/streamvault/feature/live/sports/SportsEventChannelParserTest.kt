package com.streamvault.feature.live.sports

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import com.streamvault.domain.model.Channel
import org.junit.Test

class SportsEventChannelParserTest {
    @Test
    fun parsesMatchupAndEasternStartFromEventChannelNames() {
        val event = SportsEventChannelParser.parse("CA (TSN+ 037) | NBA on TSN: Nets vs. Cavaliers (2026-10-24 19:30:05)")!!
        assertThat(event.title).isEqualTo("NBA on TSN: Nets vs. Cavaliers")
        assertThat(Instant.ofEpochMilli(event.startTime).atZone(ZoneId.of("America/Chicago")).hour).isEqualTo(18)
    }

    @Test
    fun stripsFeedLabelsAndAddsSportHintForFollowedTeams() {
        val feed = SportsEventChannelParser.parse(
            "US (ESPN+ 053) | NCAA Football: Pitt vs. #1 Miami (SkyCast) (2026-10-24 12:02:05)"
        )!!
        assertThat(feed.title).isEqualTo("NCAA Football: Pitt vs. Miami")

        val usc = SportsEventChannelParser.parse("US (Peacock 016) |  USC vs. Wisconsin (2026-10-24 19:00:05)")!!
        assertThat(usc.title).isEqualTo("USC vs. Wisconsin")
        assertThat(usc.sportHint).isEqualTo("American Football")
    }

    @Test
    fun ignoresNamesWithoutAStamp() {
        assertThat(SportsEventChannelParser.parse("US-SD | NBA TV")).isNull()
    }

    @Test
    fun eventChannelProgrammesClassifyAsCompetitiveGames() {
        val channel = Channel(
            id = 7L, name = "US (Peacock 016) |  USC vs. Wisconsin (2026-10-24 19:00:05)",
            streamUrl = "t://x", providerId = 1L, streamId = 99L,
        )
        val program = SportsEventChannelParser.programFor(channel, "99")!!
        val classification = SportsProgrammeClassifier.classify(program, channel)
        assertThat(classification.isCompetitiveEvent).isTrue()
        assertThat(classification.sport).isEqualTo(SportType.AMERICAN_FOOTBALL)
        assertThat(classification.competitors.map(SportsAliasRegistry::normalizeTeamName))
            .containsExactly("usc trojans", "wisconsin").inOrder()

        val nba = SportsEventChannelParser.programFor(
            channel.copy(name = "CA (TSN+ 037) | NBA on TSN: Nets vs. Cavaliers (2026-10-24 19:30:05)"), "99",
        )!!
        val nbaClass = SportsProgrammeClassifier.classify(nba, channel)
        assertThat(nbaClass.isCompetitiveEvent).isTrue()
        assertThat(nbaClass.sport).isEqualTo(SportType.BASKETBALL)
    }

    @Test
    fun parsesNumberedGameChannelsWithATimeOfDay() {
        // 2026-10-24 12:00 UTC is 8:00 AM Eastern the same day.
        val now = java.time.Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        val usc = SportsEventChannelParser.parse("NCAAF38: 7:30 PM USC vs Wisconsin", now)!!
        assertThat(usc.title).isEqualTo("College Football: USC vs Wisconsin")
        assertThat(usc.sportHint).isEqualTo("American Football")
        val eastern = Instant.ofEpochMilli(usc.startTime).atZone(ZoneId.of("America/New_York"))
        assertThat(eastern.hour).isEqualTo(19)
        assertThat(eastern.minute).isEqualTo(30)
        assertThat(eastern.dayOfMonth).isEqualTo(24)

        val spaced = SportsEventChannelParser.parse("NCAAF 38: 2:30pm Chattanooga at Western Carolina", now)!!
        assertThat(spaced.title).isEqualTo("College Football: Chattanooga at Western Carolina")

        assertThat(SportsEventChannelParser.parse("NCAAF 39: No Event Today", now)).isNull()
    }

    @Test
    fun nextGameFillerProgrammesAreNotGames() {
        val program = com.streamvault.domain.model.Program(
            channelId = "x", title = "Next Game: Los Angeles Lakers @ Utah Jazz on 2026-10-25 at 05:00PM EDT",
        )
        val channel = Channel(id = 1L, name = "NBA | LOS ANGELES LAKERS", streamUrl = "t://x", providerId = 1L)
        assertThat(SportsProgrammeClassifier.classify(program, channel).isCompetitiveEvent).isFalse()
    }
}
