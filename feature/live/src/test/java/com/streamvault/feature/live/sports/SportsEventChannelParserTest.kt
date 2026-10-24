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
        assertThat(feed.title).isEqualTo("NCAA Football: Pitt vs. #1 Miami")

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
}
