package com.streamvault.data.sports.espn

import com.streamvault.domain.sports.provider.CUSTOM_SPORTS_PROVIDER_TYPE_ID
import com.streamvault.domain.sports.provider.SportsProviderConfig

/**
 * APEX TV: a ready-made Custom JSON sports provider for ESPN's public scoreboard.
 *
 * It needs no account or key. ESPN reports each game's real state (scheduled, live, final), which is what
 * lets the Sports tab tell a live game from a replay of an old one. The app only reads schedule and score
 * data from it; playback always comes from the user's own playlist.
 */
object EspnPublicSportsProvider {
    const val INSTANCE_ID = "espn-public"
    const val BASE_URL = "https://site.api.espn.com"

    const val DEFINITION_JSON = """
{
  "name": "ESPN (public scoreboard)",
  "auth": { "mode": "none" },
  "scopes": [
    { "sport": "football", "leagueId": "nfl", "leagueName": "NFL" },
    { "sport": "football", "leagueId": "college-football", "leagueName": "College Football" },
    { "sport": "basketball", "leagueId": "nba", "leagueName": "NBA" },
    { "sport": "basketball", "leagueId": "wnba", "leagueName": "WNBA" },
    { "sport": "basketball", "leagueId": "mens-college-basketball", "leagueName": "College Basketball" },
    { "sport": "baseball", "leagueId": "mlb", "leagueName": "MLB" },
    { "sport": "hockey", "leagueId": "nhl", "leagueName": "NHL" }
  ],
  "events": {
    "endpoint": "/apis/site/v2/sports/{sport}/{leagueId}/scoreboard?dates={date}&limit=300",
    "dailyRequests": true,
    "dateFormat": "YYYYMMDD",
    "items": "events",
    "id": "id",
    "title": "name",
    "start": "date",
    "startFormat": "ISO_OFFSET",
    "competitors": {
      "items": "competitions[0].competitors[*]",
      "name": "team.displayName",
      "id": "team.id",
      "role": "homeAway",
      "logo": "team.logo"
    },
    "status": "status.type.state",
    "clock": "status.displayClock",
    "period": "status.period"
  }
}
"""

    fun config(): SportsProviderConfig = SportsProviderConfig(
        instanceId = INSTANCE_ID,
        typeId = CUSTOM_SPORTS_PROVIDER_TYPE_ID,
        baseUrl = BASE_URL,
        customDefinitionJson = DEFINITION_JSON.trim(),
    )
}
