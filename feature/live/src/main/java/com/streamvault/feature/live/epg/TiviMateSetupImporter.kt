package com.streamvault.feature.live.epg

import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.first

/**
 * APEX TV: one-time import of the favourites and custom groups Darryn had in TiviMate (read from its screens on
 * 2026-10-25). Channels are matched to this playlist by name; names that don't match are skipped.
 */
internal object TiviMateSetupImporter {
    val favorites = listOf(
        "US: SPECTRUM SPORTSNET - LAKERS", "US: SPORTSNET LA - DODGERS", "US: ESPN 2", "US: ESPN NEWS",
        "US: FOX SPORTS 1", "US: WWE NETWORK", "PC: WWE Network", "US: FOX EAST",
        "PA | Philadelphia | FOX WTXF", "US: USA NETWORK EAST", "US: TBS EAST", "CA | Los Angeles | ABC KABC",
        "US: TNT EAST", "US: SPORTSNET LA - DODGERS", "MLB Los Angeles Dodgers", "TEAM: Los Angeles Chargers",
        "US: ESPN", "US: ESPN 2", "US: WWE NETWORK", "US: FOX SPORTS 1", "US: USA NETWORK WEST",
        "PPV: WWE MONDAY NIGHT RAW",
    )

    val groups: Map<String, List<String>> = linkedMapOf(
        "Dax's List" to listOf(
            "US/UK: BABY TV", "US: Babyfirst TV", "US: BOOMERANG", "US: DISNEY CHANNEL", "US: DISNEY JR",
            "US: NICK JR EAST", "US: NICK JR WEST", "US: PBS KIDS", "US: PBS KIDS WEST (SoCal)", "CA: TREEHOUSE",
            "Music Choice 40: Kids Movie Soundtracks Audio", "Music Choice 41: Kidz Only Music Videos",
            "Music Choice 42: Kidz Only Audio Channel", "Music Choice 91: Toddler Tunes Music Videos",
            "Music Choice 92: Toddler Tunes Audio Channel",
        ),
        "LA TEAMS" to listOf(
            "US-S: SPECTRUM SPORTSNET - LAKERS", "US-S: SPORTSNET LA - DODGERS", "US-S: ESPN",
            "NBA | LOS ANGELES LAKERS", "MLB Los Angeles Dodgers", "TEAM: Los Angeles Chargers", "US | NBA TV HD",
            "CA | Los Angeles | ABC KABC", "US-S: SPECTRUM SPORTSNET - LAKERS", "US-S: SPORTSNET LA - DODGERS",
            "CA | Los Angeles | NBC KNBC", "NFL Network", "NFL Prime TNF Thursday Night Football",
        ),
        "WWE" to listOf(
            "PPV: WWE MONDAY NIGHT RAW", "US: USA NETWORK WEST", "US-S: WWE NETWORK",
            "PA | Philadelphia | CW WPHL", "PC: WWE Network", "ZA: SuperSport Variety 1",
        ),
        "News" to listOf(
            "US: ABC NEWS", "US: BBC WORLD NEWS", "US: CBS NEWS", "US: CNN", "US: FOX NEWS", "US: MSNBC - MS NOW",
            "US: NBC NEWS NOW", "US: NBC WEST", "US: Newsmax", "US: NEWSNATION",
        ),
        "My Locals" to listOf(
            "CA | Los Angeles | ABC KABC", "CA | Los Angeles | CBS KCBS", "CA | Los Angeles | CW KTLA",
            "CA | Los Angeles | FOX KTTV", "CA | Los Angeles | NBC KNBC", "PA | Philadelphia | ABC WPVI",
            "PA | Philadelphia | CBS KYW", "PA | Philadelphia | FOX WTXF", "PA | Philadelphia | NBC WCAU",
            "CA | Salinas | NBC KSBW", "NV | Henderson | FOX KVVU", "NV | Las Vegas | ABC KTNV",
            "NV | Las Vegas | CBS KLAS", "NV | Las Vegas | NBC KSNV", "TX | San Antonio | ABC KSAT",
            "TX | San Antonio | CBS KENS", "TX | San Antonio | FOX KABB", "TX | San Antonio | NBC WOAI",
            "CA | San Diego | ABC KGTV", "CA | San Diego | CBS KFMB", "CA | San Diego | FOX KSWB",
            "CA | San Diego | NBC KNSD",
        ),
    )

    private fun key(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Returns a short report: how many channels were matched and which names were not found. */
    suspend fun import(
        providerId: Long,
        channelRepository: ChannelRepository,
        favoriteRepository: FavoriteRepository,
    ): String {
        val channels = channelRepository.getChannels(providerId).first()
        val byKey = HashMap<String, Channel>()
        // Prefer channels without errors, then the shortest id, so repeats resolve the same way each time.
        channels.sortedWith(compareBy<Channel> { it.errorCount }.thenBy { it.id }).forEach { byKey.putIfAbsent(key(it.name), it) }
        val missing = mutableListOf<String>()
        fun find(name: String): Channel? = byKey[key(name)].also { if (it == null) missing += name }

        var matched = 0
        favorites.distinct().forEach { name ->
            find(name)?.let {
                favoriteRepository.addFavorite(providerId, it.id, ContentType.LIVE, null)
                matched++
            }
        }
        val existing = favoriteRepository.getGroups(providerId, ContentType.LIVE).first().associateBy { it.name }
        groups.forEach { (groupName, names) ->
            val group = existing[groupName]
                ?: favoriteRepository.createGroup(providerId, groupName, null, ContentType.LIVE).getOrNull()
                ?: return@forEach
            names.distinct().forEach { name ->
                find(name)?.let {
                    favoriteRepository.addFavorite(providerId, it.id, ContentType.LIVE, group.id)
                    matched++
                }
            }
        }
        return "matched=$matched missing=${missing.distinct()}"
    }
}
