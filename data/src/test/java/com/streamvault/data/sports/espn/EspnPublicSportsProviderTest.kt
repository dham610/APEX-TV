package com.streamvault.data.sports.espn

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.sports.custom.CustomSportsProviderDefinition
import com.streamvault.data.sports.custom.CustomSportsProviderJsonMapper
import com.streamvault.domain.sports.provider.ProviderGameState
import com.streamvault.domain.sports.provider.SportsProviderResult
import kotlinx.serialization.json.Json
import org.junit.Test

class EspnPublicSportsProviderTest {
    // Trimmed copy of a real ESPN NBA scoreboard response.
    private val scoreboard = """{"events": [{"id": "401909853", "date": "2026-10-24T22:00Z", "name": "Miami Heat at Charlotte Hornets", "status": {"displayClock": "0.0", "period": 0, "type": {"name": "STATUS_SCHEDULED", "state": "pre", "detail": "Sat, October 24th at 6:00 PM EDT"}}, "competitions": [{"competitors": [{"homeAway": "home", "score": "0", "team": {"id": "30", "displayName": "Charlotte Hornets", "logo": "https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/cha.png"}}, {"homeAway": "away", "score": "0", "team": {"id": "14", "displayName": "Miami Heat", "logo": "https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/mia.png"}}], "broadcasts": [{"market": "away", "names": ["NBA League Pass"]}]}]}, {"id": "401909854", "date": "2026-10-24T23:30Z", "name": "Brooklyn Nets at Cleveland Cavaliers", "status": {"displayClock": "0.0", "period": 0, "type": {"name": "STATUS_SCHEDULED", "state": "pre", "detail": "Sat, October 24th at 7:30 PM EDT"}}, "competitions": [{"competitors": [{"homeAway": "home", "score": "0", "team": {"id": "5", "displayName": "Cleveland Cavaliers", "logo": "https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/cle.png"}}, {"homeAway": "away", "score": "0", "team": {"id": "17", "displayName": "Brooklyn Nets", "logo": "https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/bkn.png"}}], "broadcasts": [{"market": "away", "names": ["NBA League Pass", "DAZN (YES)"]}, {"market": "home", "names": ["DAZN CLE"]}]}]}]}"""

    @Test
    fun definitionParsesAndAdvertisesEventsAndStatus() {
        val definition = CustomSportsProviderDefinition.parse(EspnPublicSportsProvider.DEFINITION_JSON.trim())
        assertThat(definition.scopes.map { it.leagueId }).contains("nba")
        assertThat(definition.capabilities.map { it.name }).containsAtLeast("EVENTS", "GAME_STATUS")
    }

    @Test
    fun mapsEspnScoreboardIntoEventsWithTeamsAndState() {
        val definition = CustomSportsProviderDefinition.parse(EspnPublicSportsProvider.DEFINITION_JSON.trim())
        val result = CustomSportsProviderJsonMapper.events(
            definition.events,
            Json.parseToJsonElement(scoreboard),
            sportFallback = "basketball",
            leagueIdFallback = "nba",
            leagueNameFallback = "NBA",
        ) as SportsProviderResult.Success
        val events = result.value.items
        assertThat(events).hasSize(2)
        val first = events.first()
        assertThat(first.competitors.map { it.name }).containsExactly("Charlotte Hornets", "Miami Heat")
        assertThat(first.status?.state).isEqualTo(ProviderGameState.SCHEDULED)
    }
}
