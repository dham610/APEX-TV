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

    /** Bump when DEFINITION_JSON changes so installs that still have ESPN pick up the new version. */
    const val DEFINITION_VERSION = 2

    // Notes on the mapping:
    // - College scopes ask for all of Division I ("groups"); without it ESPN returns featured games only.
    // - competitions[-1] is the game itself for team sports and the main event for UFC cards.
    // - geoBroadcasts lists the TV networks and streaming services carrying each game.
    const val DEFINITION_JSON = """
{
  "name": "ESPN (public scoreboard)",
  "auth": { "mode": "none" },
  "scopes": [
    { "sport": "football", "leagueId": "nfl", "leagueName": "NFL" },
    { "sport": "football", "leagueId": "college-football", "leagueName": "College Football", "query": { "groups": "90" } },
    { "sport": "basketball", "leagueId": "nba", "leagueName": "NBA" },
    { "sport": "basketball", "leagueId": "wnba", "leagueName": "WNBA" },
    { "sport": "basketball", "leagueId": "mens-college-basketball", "leagueName": "College Basketball", "query": { "groups": "50" } },
    { "sport": "basketball", "leagueId": "womens-college-basketball", "leagueName": "Women's College Basketball", "query": { "groups": "50" } },
    { "sport": "baseball", "leagueId": "mlb", "leagueName": "MLB" },
    { "sport": "hockey", "leagueId": "nhl", "leagueName": "NHL" },
    { "sport": "mma", "leagueId": "ufc", "leagueName": "UFC" }
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
      "items": "competitions[-1].competitors[*]",
      "name": "team.displayName|athlete.displayName",
      "id": "team.id|athlete.id",
      "role": "homeAway",
      "logo": "team.logo"
    },
    "status": "status.type.state",
    "clock": "status.displayClock",
    "period": "status.period",
    "broadcasters": {
      "items": "competitions[-1].geoBroadcasts[*]",
      "name": "media.shortName",
      "type": "type.shortName",
      "language": "lang",
      "country": "region"
    }
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
