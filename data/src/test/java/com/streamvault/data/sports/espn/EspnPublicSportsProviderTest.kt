package com.streamvault.data.sports.espn

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.sports.custom.CustomSportsProviderDefinition
import com.streamvault.data.sports.custom.CustomSportsProviderJsonMapper
import com.streamvault.domain.sports.provider.ProviderBroadcastType
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

    // Trimmed copy of a real ESPN UFC card: each bout is its own competition and the main event is last.
    private val ufcCard = """{"events": [{"id": "600058474", "date": "2026-10-24T14:00Z", "name": "UFC 333: Volkanovski vs. Evloev", "status": {"type": {"state": "pre"}}, "competitions": [{"competitors": [{"athlete": {"id": "1", "displayName": "Nicolle Caliari"}}, {"athlete": {"id": "2", "displayName": "Iasmin Lucindo"}}], "geoBroadcasts": [{"type": {"shortName": "Streaming"}, "media": {"shortName": "Paramount+"}, "lang": "en", "region": "us"}]}, {"competitors": [{"athlete": {"id": "3", "displayName": "Alexander Volkanovski"}}, {"athlete": {"id": "4", "displayName": "Movsar Evloev"}}], "geoBroadcasts": [{"type": {"shortName": "Streaming"}, "media": {"shortName": "Paramount+"}, "lang": "en", "region": "us"}]}]}]}"""

    private val nflGame = """{"events": [{"id": "401", "date": "2026-10-25T13:30Z", "name": "Pittsburgh Steelers at New Orleans Saints", "status": {"type": {"state": "pre"}}, "competitions": [{"competitors": [{"homeAway": "home", "team": {"id": "18", "displayName": "New Orleans Saints"}}, {"homeAway": "away", "team": {"id": "23", "displayName": "Pittsburgh Steelers"}}], "geoBroadcasts": [{"type": {"shortName": "TV"}, "market": {"type": "National"}, "media": {"shortName": "NFL Net"}, "lang": "en", "region": "us"}]}]}]}"""

    private fun map(body: String, sport: String, leagueId: String) = (CustomSportsProviderJsonMapper.events(
        CustomSportsProviderDefinition.parse(EspnPublicSportsProvider.DEFINITION_JSON.trim()).events,
        Json.parseToJsonElement(body),
        sportFallback = sport,
        leagueIdFallback = leagueId,
        leagueNameFallback = null,
    ) as SportsProviderResult.Success).value.items

    @Test
    fun collegeScopesRequestAllOfDivisionOneAndProScopesDoNot() {
        val scopes = CustomSportsProviderDefinition.parse(EspnPublicSportsProvider.DEFINITION_JSON.trim()).scopes
            .associateBy { it.leagueId }
        assertThat(scopes.getValue("college-football").query).containsExactly("groups", "90")
        assertThat(scopes.getValue("mens-college-basketball").query).containsExactly("groups", "50")
        assertThat(scopes.getValue("womens-college-basketball").query).containsExactly("groups", "50")
        assertThat(scopes.getValue("nfl").query).isEmpty()
        assertThat(scopes.keys).contains("ufc")
    }

    @Test
    fun ufcCardUsesMainEventFightersAndStreamingService() {
        val event = map(ufcCard, "mma", "ufc").single()
        assertThat(event.title).isEqualTo("UFC 333: Volkanovski vs. Evloev")
        assertThat(event.competitors.map { it.name }).containsExactly("Alexander Volkanovski", "Movsar Evloev")
        assertThat(event.broadcasters.map { it.name }).containsExactly("Paramount+")
        assertThat(event.broadcasters.single().type).isEqualTo(ProviderBroadcastType.STREAMING_SERVICE)
    }

    @Test
    fun teamGameCarriesItsTvNetwork() {
        val event = map(nflGame, "football", "nfl").single()
        assertThat(event.competitors.map { it.name }).containsExactly("New Orleans Saints", "Pittsburgh Steelers")
        assertThat(event.broadcasters.map { it.name }).containsExactly("NFL Net")
        assertThat(event.broadcasters.single().type).isEqualTo(ProviderBroadcastType.LINEAR_TV)
    }
}
