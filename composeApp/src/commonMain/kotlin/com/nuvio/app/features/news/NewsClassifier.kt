package com.nuvio.app.features.news

/** Which league or sport a story belongs to. [leagueId] is a Nuvio catalogue id when the story names one. */
internal data class NewsTag(val leagueId: String?, val short: String, val sportKey: String)

/**
 * Publisher feeds are general: one CBS or FOX feed holds every sport. The story's own address says
 * most reliably what it is about (cbssports.com/nhl/..., foxsports.com/stories/soccer/...), so that
 * comes first; league names in the headline and summary are the fallback.
 */
internal object NewsClassifier {
    private fun tag(leagueId: String?, short: String, sport: String) = NewsTag(leagueId, short, sport)

    private val NFL = tag("football/nfl", "NFL", "AMERICAN_FOOTBALL")
    private val NCAAF = tag("football/college-football", "NCAAF", "AMERICAN_FOOTBALL")
    private val NBA = tag("basketball/nba", "NBA", "BASKETBALL")
    private val WNBA = tag("basketball/wnba", "WNBA", "BASKETBALL")
    private val NCAAM = tag("basketball/mens-college-basketball", "NCAAM", "BASKETBALL")
    private val NHL = tag("hockey/nhl", "NHL", "HOCKEY")
    private val MLB = tag("baseball/mlb", "MLB", "BASEBALL")
    private val SOCCER = tag(null, "Soccer", "FOOTBALL")
    private val EPL = tag("soccer/eng.1", "EPL", "FOOTBALL")
    private val MLS = tag("soccer/usa.1", "MLS", "FOOTBALL")
    private val UCL = tag("soccer/uefa.champions", "UCL", "FOOTBALL")
    private val LALIGA = tag("soccer/esp.1", "LALIGA", "FOOTBALL")
    private val BUNDES = tag("soccer/ger.1", "BUNDES", "FOOTBALL")
    private val SERIEA = tag("soccer/ita.1", "SERIE A", "FOOTBALL")
    private val LIGUE1 = tag("soccer/fra.1", "LIGUE 1", "FOOTBALL")
    private val F1 = tag("racing/f1", "F1", "RACING")
    private val NASCAR = tag("racing/nascar-premier", "NASCAR", "RACING")
    private val MOTOGP = tag("guide/motogp", "MOTOGP", "RACING")
    private val GOLF = tag(null, "Golf", "GOLF")
    private val TENNIS = tag(null, "Tennis", "TENNIS")
    private val UFC = tag("mma/ufc", "UFC", "FIGHTING")
    private val BOXING = tag("guide/boxing", "BOXING", "FIGHTING")
    private val WWE = tag("guide/wwe", "WWE", "WRESTLING")
    private val CRICKET = tag(null, "Cricket", "CRICKET")
    private val RUGBY = tag(null, "Rugby", "RUGBY")
    private val DARTS = tag(null, "Darts", "DARTS")
    private val SNOOKER = tag(null, "Snooker", "SNOOKER")
    private val CYCLING = tag(null, "Cycling", "OTHER")

    private val BY_SLUG: Map<String, NewsTag> = mapOf(
        "nfl" to NFL, "college-football" to NCAAF, "ncaaf" to NCAAF, "cfb" to NCAAF,
        "nba" to NBA, "wnba" to WNBA, "college-basketball" to NCAAM, "ncaab" to NCAAM, "ncaam" to NCAAM,
        "nhl" to NHL, "mlb" to MLB,
        "soccer" to SOCCER, "football" to SOCCER, "mls" to MLS, "premier-league" to EPL,
        "champions-league" to UCL, "la-liga" to LALIGA, "bundesliga" to BUNDES, "serie-a" to SERIEA, "ligue-1" to LIGUE1,
        "formula1" to F1, "formula-1" to F1, "f1" to F1, "nascar" to NASCAR, "motogp" to MOTOGP,
        "golf" to GOLF, "tennis" to TENNIS, "ufc" to UFC, "mma" to UFC, "boxing" to BOXING, "wwe" to WWE,
        "cricket" to CRICKET, "rugby-union" to RUGBY, "rugby-league" to RUGBY, "darts" to DARTS, "snooker" to SNOOKER,
        "cycling" to CYCLING,
    )

    private val BY_TEXT: List<Pair<Regex, NewsTag>> = listOf(
        Regex("\\bWNBA\\b") to WNBA,
        Regex("\\bNFL\\b|Super Bowl|NFL Draft") to NFL,
        Regex("\\bNBA\\b|NBA Finals") to NBA,
        Regex("\\bNHL\\b|Stanley Cup") to NHL,
        Regex("\\bMLB\\b|World Series") to MLB,
        Regex("(?i)premier league") to EPL,
        Regex("(?i)champions league") to UCL,
        Regex("(?i)la ?liga") to LALIGA,
        Regex("(?i)bundesliga") to BUNDES,
        Regex("(?i)serie a\\b") to SERIEA,
        Regex("(?i)ligue 1") to LIGUE1,
        Regex("\\bMLS\\b") to MLS,
        Regex("(?i)formula (1|one)|\\bF1\\b|grand prix") to F1,
        Regex("\\bNASCAR\\b") to NASCAR,
        Regex("(?i)pga tour|\\bLPGA\\b|\\bLIV Golf\\b|\\bthe masters\\b") to GOLF,
        Regex("\\bATP\\b|\\bWTA\\b|Wimbledon|Roland Garros|Australian Open") to TENNIS,
        Regex("\\bUFC\\b") to UFC,
        Regex("\\bWWE\\b") to WWE,
    )

    private val ROUNDUP = Regex("(?i)top games to watch|games to watch, best bets|power rankings|weekly roundup")

    fun classify(url: String?, headline: String, summary: String, categories: List<String> = emptyList()): NewsTag? {
        if (ROUNDUP.containsMatchIn(headline)) return null
        slugOf(url)?.let { slug -> BY_SLUG[slug]?.let { return it } }

        val inHeadline = BY_TEXT.filter { (re, _) -> re.containsMatchIn(headline) }.map { it.second }.distinct()
        if (inHeadline.size == 1) return inHeadline.first()
        if (inHeadline.size > 1) return null

        val rest = (listOf(summary) + categories).joinToString(" ")
        val inBody = BY_TEXT.filter { (re, _) -> re.containsMatchIn(rest) }.map { it.second }.distinct()
        return inBody.singleOrNull()
    }

    private fun slugOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val noScheme = url.substringAfter("://", url)
        val host = noScheme.substringBefore('/').lowercase().removePrefix("www.")
        val path = noScheme.substringAfter('/', "").substringBefore('?').split('/').filter { it.isNotEmpty() }
        val segment = when {
            host.endsWith("foxsports.com") -> path.getOrNull(if (path.firstOrNull() == "stories") 1 else 0)
            host.endsWith("bbc.co.uk") || host.endsWith("bbc.com") -> if (path.firstOrNull() == "sport") path.getOrNull(1) else null
            host.endsWith("espn.com") || host.endsWith("cbssports.com") || host.endsWith("skysports.com") || host.endsWith("nbcsports.com") -> path.firstOrNull()
            else -> null
        }
        return segment?.lowercase()
    }

    /** True for sportsbook promotions and prediction-market ads, which are not news. */
    fun isPromo(url: String?, headline: String): Boolean {
        val path = url.orEmpty().lowercase()
        if ("/betting/" in path || "/prediction/" in path || "promo-code" in path) return true
        return PROMO.containsMatchIn(headline)
    }

    private val PROMO = Regex(
        "(?i)promo code|promo codes|betting promos?|sportsbook (promo|bonus)|bonus bets?|polymarket|kalshi|draftkings|fanduel|betmgm|caesars sportsbook|bet365|get \\$\\d+|\\$\\d+ (bonus|in bonus)|free bets?|odds boost",
    )
}
