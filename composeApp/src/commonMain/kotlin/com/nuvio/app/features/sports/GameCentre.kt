package com.nuvio.app.features.sports

/*
 * The Game Centre's data: one request to ESPN's game summary, read into what the three tabs show.
 * ESPN serves the same shapes for every sport it covers (plays with the people in them, box score
 * tables, leaders, team stats), so one reader handles hockey, basketball, football, baseball and
 * soccer. Anything a sport doesn't give is simply empty and its section is not drawn.
 */

/** One thing that happened: a goal, a penalty, a basket, a card. */
data class CentreEvent(
    val id: String,
    val teamAbbr: String?,
    val teamLogo: String?,
    /** Score straight after it, "1–0", for scoring events. */
    val scoreAfter: String?,
    /** "3RD PERIOD", "2ND QUARTER": the heading it sits under. */
    val periodHeading: String,
    val periodNumber: Int,
    val clock: String?,
    val headline: String,
    val detail: String?,
    val secondary: String?,
    /** "PP", "SH", "EN"… small tags next to the clock. */
    val badges: List<String>,
    val headshot: String?,
    /** The filter chip this falls under: "Goals", "Penalties", "Shots"… */
    val category: String,
    val scoring: Boolean,
)

/** A game leader: "Goals · Elias Lindholm · 1". */
data class CentreLeader(
    val category: String,
    val name: String,
    val teamAbbr: String?,
    val headshot: String?,
    val value: String,
    val position: String?,
)

data class BoxRow(val name: String, val headshot: String?, val position: String?, val jersey: String?, val stats: List<String>)

data class BoxGroup(val title: String, val labels: List<String>, val rows: List<BoxRow>)

data class BoxTeam(val abbr: String, val logo: String?, val groups: List<BoxGroup>)

data class CentreOfficial(val role: String, val name: String)

data class GameCentre(
    val scoring: List<CentreEvent> = emptyList(),
    val plays: List<CentreEvent> = emptyList(),
    val filters: List<String> = emptyList(),
    val leaders: List<CentreLeader> = emptyList(),
    val box: List<BoxTeam> = emptyList(),
    /** "16 SHOTS" under each team's name in the header, keyed by team id. */
    val shots: Map<String, String> = emptyMap(),
    val officials: List<CentreOfficial> = emptyList(),
    val attendance: Int? = null,
    val venue: String? = null,
    val city: String? = null,
) {
    val isEmpty: Boolean get() = scoring.isEmpty() && plays.isEmpty() && leaders.isEmpty() && box.isEmpty()
}

internal object GameCentreReader {
    private fun JSONObject.s(name: String): String? = optString(name, "").takeIf { it.isNotBlank() && it != "null" }

    private val PENALTIES = Regex("(?i)tripping|hooking|holding|slashing|roughing|cross.?checking|high.?sticking|interference|misconduct|delay(ed|ing)? (of )?game|fighting|boarding|charging|elbowing|kneeing|too many men|unsportsmanlike|penalty")

    fun read(root: JSONObject, ev: SportsEvent, sport: Sport): GameCentre {
        val teams = teamIndex(root)
        val headshots = HashMap<String, String>()
        val plays: List<CentreEvent>
        val scoring: List<CentreEvent>
        val filters: List<String>
        when {
            root.optJSONArray("keyEvents") != null && sport == Sport.FOOTBALL -> {
                val events = soccerEvents(root, teams, headshots)
                plays = events
                scoring = events.filter { it.scoring }
                filters = listOf("All", "Goals", "Cards", "Subs")
            }
            root.optJSONArray("scoringPlays") != null && root.optJSONArray("plays") == null -> {
                // American football: ESPN groups plays into drives.
                val flat = footballPlays(root, teams, headshots)
                plays = flat
                scoring = flat.filter { it.scoring }
                filters = listOf("All", "Scoring")
            }
            else -> {
                val raw = root.optJSONArray("plays").objs()
                val all = playEvents(raw, teams, sport, headshots)
                plays = all
                scoring = all.filter { it.scoring }
                filters = when (sport) {
                    Sport.HOCKEY -> listOf("All", "Goals", "Penalties", "Shots", "Hits")
                    Sport.BASKETBALL -> listOf("All", "Scoring", "Rebounds", "Turnovers", "Fouls")
                    else -> listOf("All", "Scoring")
                }.filter { f -> f == "All" || all.any { it.category == f } }
            }
        }
        val finalScoring = if (scoring.isEmpty() && sport != Sport.FOOTBALL) {
            root.optJSONArray("scoringPlays").objs().mapIndexedNotNull { i, p -> eventOf(p, i, teams, sport, headshots, forceScoring = true) }
        } else scoring
        return GameCentre(
            scoring = finalScoring,
            plays = plays,
            filters = filters.takeIf { plays.isNotEmpty() } ?: emptyList(),
            leaders = leaders(root, teams),
            box = box(root, teams),
            shots = shots(root),
            officials = officials(root),
            attendance = root.optJSONObject("gameInfo")?.optInt("attendance", 0)?.takeIf { it > 0 },
            venue = root.optJSONObject("gameInfo")?.optJSONObject("venue")?.s("fullName"),
            city = root.optJSONObject("gameInfo")?.optJSONObject("venue")?.optJSONObject("address")?.let { listOfNotNull(it.s("city"), it.s("state")).joinToString(", ") }?.takeIf { it.isNotBlank() },
        )
    }

    // ---- teams --------------------------------------------------------------------------------

    private class TeamRef(val id: String, val abbr: String, val logo: String?)

    private fun teamIndex(root: JSONObject): Map<String, TeamRef> {
        val out = HashMap<String, TeamRef>()
        fun add(t: JSONObject?) {
            t ?: return
            val id = t.s("id") ?: return
            val logo = t.s("logo") ?: t.optJSONArray("logos").objs().firstOrNull()?.s("href")
            out[id] = TeamRef(id, t.s("abbreviation") ?: t.s("shortDisplayName") ?: t.s("displayName") ?: "", logo)
        }
        root.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors").objs().forEach { add(it.optJSONObject("team")) }
        root.optJSONObject("boxscore")?.optJSONArray("teams").objs().forEach { add(it.optJSONObject("team")) }
        return out
    }

    // ---- plays --------------------------------------------------------------------------------

    private fun playEvents(raw: List<JSONObject>, teams: Map<String, TeamRef>, sport: Sport, heads: MutableMap<String, String>): List<CentreEvent> {
        val kept = if (sport == Sport.BASEBALL) raw.filter { it.s("summaryType")?.uppercase() != "P" }.ifEmpty { raw } else raw
        return kept.mapIndexedNotNull { i, p -> eventOf(p, i, teams, sport, heads) }
    }

    private fun footballPlays(root: JSONObject, teams: Map<String, TeamRef>, heads: MutableMap<String, String>): List<CentreEvent> {
        val out = ArrayList<CentreEvent>()
        val drives = root.optJSONObject("drives")
        val all = drives?.optJSONArray("previous").objs() + listOfNotNull(drives?.optJSONObject("current"))
        for (d in all) d.optJSONArray("plays").objs().forEachIndexed { i, p ->
            eventOf(p, out.size + i, teams, Sport.AMERICAN_FOOTBALL, heads)?.let { out += it }
        }
        if (out.none { it.scoring }) root.optJSONArray("scoringPlays").objs().forEachIndexed { i, p ->
            eventOf(p, i, teams, Sport.AMERICAN_FOOTBALL, heads, forceScoring = true)?.let { out += it }
        }
        return out
    }

    private fun soccerEvents(root: JSONObject, teams: Map<String, TeamRef>, heads: MutableMap<String, String>): List<CentreEvent> =
        root.optJSONArray("keyEvents").objs().mapIndexedNotNull { i, e ->
            val type = e.optJSONObject("type")?.s("text").orEmpty()
            if (type.contains("Kickoff", true)) return@mapIndexedNotNull null
            val team = teams[e.optJSONObject("team")?.s("id")]
            val scoring = e.optBoolean("scoringPlay")
            val category = when {
                scoring -> "Goals"
                type.contains("card", true) -> "Cards"
                type.contains("substitution", true) -> "Subs"
                else -> "Other"
            }
            val player = e.optJSONArray("participants").objs().firstOrNull()?.optJSONObject("athlete")?.s("displayName")
            val n = e.optJSONObject("period")?.optInt("number", 0) ?: 0
            CentreEvent(
                id = e.s("id") ?: "k$i",
                teamAbbr = team?.abbr ?: e.optJSONObject("team")?.s("displayName"),
                teamLogo = team?.logo,
                scoreAfter = null,
                periodHeading = when (n) { 1 -> "1ST HALF"; 2 -> "2ND HALF"; 3 -> "EXTRA TIME"; 4 -> "EXTRA TIME"; 5 -> "PENALTIES"; else -> "MATCH" },
                periodNumber = n,
                clock = e.optJSONObject("clock")?.s("displayValue"),
                headline = e.s("shortText") ?: player ?: type,
                detail = if (scoring || category != "Other") type.takeIf { it.isNotBlank() && !e.s("shortText").orEmpty().contains(it, true) } else null,
                secondary = null,
                badges = emptyList(),
                headshot = null,
                category = category,
                scoring = scoring,
            )
        }.filter { it.category != "Other" || it.headline.isNotBlank() }

    private fun eventOf(
        p: JSONObject, index: Int, teams: Map<String, TeamRef>, sport: Sport, heads: MutableMap<String, String>, forceScoring: Boolean = false,
    ): CentreEvent? {
        val text = p.s("text") ?: p.s("shortDescription") ?: return null
        val type = p.optJSONObject("type")?.s("text").orEmpty()
        val team = teams[p.optJSONObject("team")?.s("id")]
        val scoring = forceScoring || p.optBoolean("scoringPlay") || p.optInt("scoreValue", 0) > 0 && sport == Sport.BASKETBALL
        val number = p.optJSONObject("period")?.optInt("number", 0) ?: 0
        val display = p.optJSONObject("period")?.s("displayValue")
        val half = p.optJSONObject("period")?.s("type")
        val parts = p.optJSONArray("participants").objs()
        fun named(kind: String): List<String> = parts.filter { it.s("type")?.contains(kind, true) == true }.mapNotNull { it.optJSONObject("athlete")?.let { a -> a.s("displayName") ?: a.s("shortName") } }
        val firstAthlete = parts.firstOrNull()?.optJSONObject("athlete")
        val head = firstAthlete?.optJSONObject("headshot")?.s("href")
        val away = p.opt("awayScore")?.toString()
        val home = p.opt("homeScore")?.toString()
        val after = if (scoring && away != null && home != null) "$away–$home" else null
        val strength = p.optJSONObject("strength")?.s("text").orEmpty()
        val badges = buildList {
            if (scoring && strength.contains("power", true)) add("PP")
            if (scoring && strength.contains("short", true)) add("SH")
            if (scoring && text.contains("empty net", true)) add("EN")
        }
        var headline = text
        var detail: String? = null
        var secondary: String? = null
        if (sport == Sport.HOCKEY && scoring) {
            val scorer = named("scorer").firstOrNull()
            val assists = parts.filter { it.s("type")?.contains("assist", true) == true }.mapNotNull { it.optJSONObject("athlete")?.s("displayName") }
            if (scorer != null) {
                headline = scorer
                detail = Regex("""Goal \(\d+\)\s*([^,]+)""").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                secondary = assists.takeIf { it.isNotEmpty() }?.joinToString(", ")
            }
        } else if (sport == Sport.BASKETBALL && scoring) {
            detail = type.takeIf { it.isNotBlank() }
        } else if (sport == Sport.AMERICAN_FOOTBALL && scoring) {
            detail = type.takeIf { it.isNotBlank() }
        }
        val category = when (sport) {
            Sport.HOCKEY -> when {
                type.equals("Goal", true) || scoring -> "Goals"
                PENALTIES.containsMatchIn(type) -> "Penalties"
                type.equals("Shot", true) || type.equals("Missed", true) || type.equals("Blocked", true) || type.contains("shot", true) -> "Shots"
                type.equals("Hit", true) -> "Hits"
                else -> "Other"
            }
            Sport.BASKETBALL -> when {
                scoring -> "Scoring"
                type.contains("rebound", true) -> "Rebounds"
                type.contains("turnover", true) || type.contains("steal", true) -> "Turnovers"
                type.contains("foul", true) -> "Fouls"
                else -> "Other"
            }
            else -> if (scoring) "Scoring" else "Other"
        }
        val tag = when {
            number <= 0 -> "GAME"
            sport == Sport.HOCKEY -> if (number > 3) (if (number == 4) "OVERTIME" else "SHOOTOUT") else ordinal(number) + " PERIOD"
            sport == Sport.BASKETBALL || sport == Sport.AMERICAN_FOOTBALL -> if (number > 4) "OVERTIME" else ordinal(number) + " QUARTER"
            sport == Sport.BASEBALL -> (half?.uppercase()?.let { "$it " } ?: "") + ordinal(number).uppercase() + " INNING"
            else -> display?.uppercase() ?: ordinal(number)
        }
        return CentreEvent(
            id = p.s("id") ?: "p$index",
            teamAbbr = team?.abbr,
            teamLogo = team?.logo,
            scoreAfter = after,
            periodHeading = tag,
            periodNumber = number,
            clock = p.optJSONObject("clock")?.s("displayValue"),
            headline = headline,
            detail = detail,
            secondary = secondary,
            badges = badges,
            headshot = head,
            category = category,
            scoring = scoring,
        )
    }

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}TH"
        n % 10 == 1 -> "${n}ST"
        n % 10 == 2 -> "${n}ND"
        n % 10 == 3 -> "${n}RD"
        else -> "${n}TH"
    }

    // ---- leaders ------------------------------------------------------------------------------

    private fun leaders(root: JSONObject, teams: Map<String, TeamRef>): List<CentreLeader> {
        val out = ArrayList<CentreLeader>()
        for (group in root.optJSONArray("leaders").objs()) {
            val team = teams[group.optJSONObject("team")?.s("id")]
            for (cat in group.optJSONArray("leaders").objs()) {
                val top = cat.optJSONArray("leaders").objs().firstOrNull() ?: continue
                val a = top.optJSONObject("athlete") ?: continue
                out += CentreLeader(
                    category = cat.s("displayName") ?: cat.s("name") ?: continue,
                    name = a.s("displayName") ?: a.s("shortName") ?: continue,
                    teamAbbr = team?.abbr,
                    headshot = a.optJSONObject("headshot")?.s("href"),
                    value = top.s("displayValue") ?: top.s("mainStat")?.let { it } ?: "",
                    position = a.optJSONObject("position")?.s("abbreviation"),
                )
            }
        }
        return out
    }

    // ---- box score ----------------------------------------------------------------------------

    private fun box(root: JSONObject, teams: Map<String, TeamRef>): List<BoxTeam> {
        val players = root.optJSONObject("boxscore")?.optJSONArray("players").objs()
        return players.mapNotNull { entry ->
            val team = teams[entry.optJSONObject("team")?.s("id")]
            val groups = entry.optJSONArray("statistics").objs().mapNotNull { g ->
                val name = g.s("name")
                if (name.equals("skaters", true)) return@mapNotNull null
                val labels = g.optJSONArray("labels")?.let { l -> (0 until l.length()).map { l.optString(it) } } ?: return@mapNotNull null
                val rows = g.optJSONArray("athletes").objs().mapNotNull { r ->
                    val a = r.optJSONObject("athlete") ?: return@mapNotNull null
                    val stats = r.optJSONArray("stats")?.let { s -> (0 until s.length()).map { s.optString(it) } } ?: return@mapNotNull null
                    if (r.optBoolean("didNotPlay")) return@mapNotNull null
                    BoxRow(
                        name = a.s("shortName") ?: a.s("displayName") ?: return@mapNotNull null,
                        headshot = a.optJSONObject("headshot")?.s("href"),
                        position = a.optJSONObject("position")?.s("abbreviation"),
                        jersey = a.s("jersey"),
                        stats = stats,
                    )
                }
                if (rows.isEmpty()) null else BoxGroup(groupTitle(name, labels), labels, rows)
            }
            if (groups.isEmpty()) null else BoxTeam(team?.abbr ?: entry.optJSONObject("team")?.s("abbreviation").orEmpty(), team?.logo, groups)
        }
    }

    private fun groupTitle(name: String?, labels: List<String>): String = when (name?.lowercase()) {
        null, "" -> if (labels.firstOrNull() == "IP") "Pitching" else if (labels.firstOrNull() == "H-AB") "Batting" else "Players"
        "forwards" -> "Forwards"
        "defenses" -> "Defense"
        "goalies" -> "Goalies"
        else -> name.replaceFirstChar { it.uppercase() }
    }

    // ---- shots and officials ------------------------------------------------------------------

    private fun shots(root: JSONObject): Map<String, String> {
        val out = HashMap<String, String>()
        for (t in root.optJSONObject("boxscore")?.optJSONArray("teams").objs()) {
            val id = t.optJSONObject("team")?.s("id") ?: continue
            val stat = t.optJSONArray("statistics").objs().firstOrNull { it.s("name") == "shotsTotal" || it.s("name") == "totalShots" } ?: continue
            stat.s("displayValue")?.let { out[id] = "$it SHOTS" }
        }
        return out
    }

    private fun officials(root: JSONObject): List<CentreOfficial> =
        root.optJSONObject("gameInfo")?.optJSONArray("officials").objs().mapNotNull {
            CentreOfficial(it.optJSONObject("position")?.s("displayName") ?: "Official", it.s("displayName") ?: it.s("fullName") ?: return@mapNotNull null)
        }
}
