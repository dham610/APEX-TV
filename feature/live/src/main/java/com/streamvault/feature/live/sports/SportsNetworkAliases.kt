package com.streamvault.feature.live.sports

import java.util.Locale

/**
 * APEX TV: maps broadcaster names from schedule feeds (ESPN uses short forms such as "NFL Net" or "CBSSN")
 * and IPTV channel names ("US-S: NFL NETWORK", "US: FOX EAST", "AL | Birmingham | FOX WBRC") to one
 * network key, so a game listed on a network can find that network's channels.
 *
 * Keys are returned as "net:<key>" for national feeds and "aff:<key>" for local affiliates.
 * Streaming-only names ("ESPN+", "SECN+", "Paramount+") never map to a linear network.
 */
internal object SportsNetworkAliases {
    private const val NATIONAL_PREFIX = "net:"
    private const val AFFILIATE_PREFIX = "aff:"

    private val networks: Map<String, List<String>> = mapOf(
        "espn" to listOf("espn"),
        "espn2" to listOf("espn2", "espn 2"),
        "espnu" to listOf("espnu", "espn u"),
        "espnews" to listOf("espnews", "espn news"),
        "abc" to listOf("abc"),
        "cbs" to listOf("cbs"),
        "nbc" to listOf("nbc"),
        "fox" to listOf("fox", "fox sports"),
        "cw" to listOf("cw", "the cw"),
        "fs1" to listOf("fs1", "fox sports 1"),
        "fs2" to listOf("fs2", "fox sports 2"),
        "cbssn" to listOf("cbssn", "cbs sports network", "cbs sports net"),
        "btn" to listOf("btn", "big ten network", "big ten network btn", "big ten"),
        "accn" to listOf("accn", "acc network"),
        "secn" to listOf("secn", "sec network"),
        "nfln" to listOf("nfl net", "nfl network"),
        "nhln" to listOf("nhl net", "nhl network"),
        "mlbn" to listOf("mlb net", "mlb network"),
        "nbatv" to listOf("nba tv"),
        "tnt" to listOf("tnt"),
        "trutv" to listOf("trutv", "tru tv"),
        "usa" to listOf("usa net", "usa network"),
        "golf" to listOf("golf channel", "golf"),
    )

    private val keyByVariant: Map<String, String> = buildMap {
        networks.forEach { (key, variants) -> variants.forEach { put(it, key) } }
    }

    /** Networks whose local stations appear as "<NETWORK> <CALL SIGN>", e.g. "FOX WBRC". */
    private val affiliateNetworks = setOf("abc", "cbs", "nbc", "fox", "cw")

    private val decoration = setOf(
        "east", "west", "hd", "fhd", "uhd", "4k", "sd", "alt", "backup", "feed", "raw", "low", "bw", "bandwidth",
        "us", "ca", "uk",
    )
    private val callSign = Regex("[kw][a-z]{2,3}")
    private val bracketedCallSign = Regex("\\([KkWw][A-Za-z]{2,3}\\)")
    private val foreignPrefixes = setOf(
        "ca", "au", "uk", "car", "mx", "nz", "ie", "in", "br", "ar", "es", "fr", "de", "it", "pt", "nl", "za",
        "ph", "latam", "latino", "pl", "tr", "se", "no", "dk", "fi", "gr", "hk", "sg", "my",
    )

    /** Every token a channel name could contain for this broadcaster, used to find candidate channels. */
    fun searchTokens(broadcasterName: String): Set<String> {
        val key = broadcasterKey(broadcasterName) ?: return emptySet()
        return networks[key].orEmpty().flatMapTo(mutableSetOf()) { it.split(' ') }
    }

    /** Keys for a broadcaster from a schedule feed, or empty when it is streaming-only or unknown. */
    fun keysForBroadcaster(name: String): Set<String> {
        val key = broadcasterKey(name) ?: return emptySet()
        return buildSet {
            add(NATIONAL_PREFIX + key)
            if (key in affiliateNetworks) add(AFFILIATE_PREFIX + key)
        }
    }

    /** Keys for an IPTV channel name, or empty when it is not a recognised network channel. */
    fun keysForChannel(name: String): Set<String> {
        val segments = name.split('|', ':').map(String::trim).filter(String::isNotEmpty)
        if (segments.isEmpty() || isForeignFeed(segments)) return emptySet()
        val segment = segments.last()
        // "FOX (KXII)": a call sign in brackets marks a local station, not the national feed.
        val bracketCallSign = bracketedCallSign.find(segment) != null
        val core = clean(segment).split(' ').filter(String::isNotBlank).filterNot { it in decoration }
        if (core.isEmpty()) return emptySet()
        if (bracketCallSign && core.size == 1 && core[0] in affiliateNetworks) {
            return setOf(AFFILIATE_PREFIX + core[0])
        }
        keyByVariant[core.joinToString(" ")]?.let { return setOf(NATIONAL_PREFIX + it) }
        if (core.size == 2 && core[0] in affiliateNetworks && callSign.matches(core[1])) {
            return setOf(AFFILIATE_PREFIX + core[0])
        }
        return emptySet()
    }

    /**
     * "AU: ESPN", "CAR: ESPN", "CA-S: NFL NETWORK" are other countries' feeds. Local US stations use three
     * parts ("TX | Sherman | FOX (KXII)", "CA | Los Angeles | NBC KNBC"), so state codes are left alone.
     */
    private fun isForeignFeed(segments: List<String>): Boolean {
        if (segments.size != 2) return false
        val prefix = clean(segments[0]).split(' ').firstOrNull() ?: return false
        return prefix in foreignPrefixes
    }

    /** Sort order for a channel offering a game: 0 national feed, 1 other, 2 local station, 3 foreign feed. */
    fun channelRank(name: String): Int {
        val segments = name.split('|', ':').map(String::trim).filter(String::isNotEmpty)
        if (isForeignFeed(segments)) return 3
        val keys = keysForChannel(name)
        return when {
            keys.any { it.startsWith(NATIONAL_PREFIX) } -> 0
            keys.isNotEmpty() -> 2
            else -> 1
        }
    }

    fun isAffiliateOnly(matched: Set<String>): Boolean =
        matched.isNotEmpty() && matched.all { it.startsWith(AFFILIATE_PREFIX) }

    /** True for names that should never fall back to a plain name match against linear channels. */
    fun isStreamingOnly(name: String): Boolean = name.trim().endsWith("+")

    private fun broadcasterKey(name: String): String? {
        if (isStreamingOnly(name)) return null
        val tokens = clean(name).split(' ').filter(String::isNotBlank).filterNot { it in decoration }
        return keyByVariant[tokens.joinToString(" ")]
    }

    private fun clean(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("[^a-z0-9+ ]"), " ")
        .replace("+", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
