package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_sports_broadcast
import nuvio.composeapp.generated.resources.live_tv_sports_live_badge
import nuvio.composeapp.generated.resources.live_tv_sports_no_channels
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

/** One ESPN scoreboard feed. Public, keyless endpoints, so this works the same on Android and iOS. */
data class LiveTvSportsLeague(
    val id: String,
    val label: String,
    val path: String,
)

internal val liveTvSportsLeagues = listOf(
    LiveTvSportsLeague("eng.1", "Premier League", "soccer/eng.1"),
    LiveTvSportsLeague("esp.1", "LaLiga", "soccer/esp.1"),
    LiveTvSportsLeague("ita.1", "Serie A", "soccer/ita.1"),
    LiveTvSportsLeague("ger.1", "Bundesliga", "soccer/ger.1"),
    LiveTvSportsLeague("fra.1", "Ligue 1", "soccer/fra.1"),
    LiveTvSportsLeague("uefa.champions", "Champions League", "soccer/uefa.champions"),
    LiveTvSportsLeague("uefa.europa", "Europa League", "soccer/uefa.europa"),
    LiveTvSportsLeague("idn.1", "Liga 1 Indonesia", "soccer/idn.1"),
    LiveTvSportsLeague("nba", "NBA", "basketball/nba"),
    LiveTvSportsLeague("nfl", "NFL", "football/nfl"),
    LiveTvSportsLeague("nhl", "NHL", "hockey/nhl"),
    LiveTvSportsLeague("mlb", "MLB", "baseball/mlb"),
    LiveTvSportsLeague("ufc", "UFC", "mma/ufc"),
)

enum class LiveTvSportsState { Upcoming, Live, Finished }

data class LiveTvSportsCompetitor(
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val score: String?,
    val isHome: Boolean,
    val isWinner: Boolean,
)

data class LiveTvSportsEvent(
    val id: String,
    val league: LiveTvSportsLeague,
    val name: String,
    val startMillis: Long,
    val state: LiveTvSportsState,
    val statusDetail: String,
    val competitors: List<LiveTvSportsCompetitor>,
    val broadcasts: List<String>,
)

data class LiveTvSportsUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val events: List<LiveTvSportsEvent> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

// ---------------------------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------------------------

object LiveTvSportsRepository {
    private val log = Logger.withTag("LiveTvSports")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val headers = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
    )
    private val _uiState = MutableStateFlow(LiveTvSportsUiState())
    val uiState: StateFlow<LiveTvSportsUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    /** Refreshes when stale: every minute while something is live, otherwise every five minutes. */
    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isLoading) return
        val now = Clock.System.now().toEpochMilliseconds()
        val maxAge = if (state.events.any { it.state == LiveTvSportsState.Live }) 60_000L else 5 * 60_000L
        if (state.hasLoaded && now - state.updatedAtMillis < maxAge) return
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        job = scope.launch {
            val now = Clock.System.now()
            val range = espnDates(now.toEpochMilliseconds())
            val results = withContext(Dispatchers.Default) {
                liveTvSportsLeagues.map { league ->
                    async {
                        try {
                            Result.success(fetchLeague(league, range))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            log.w { "ESPN ${league.id} failed: ${error.message}" }
                            InAppLogger.warn(SPORTS_LOG_TAG, "ESPN ${league.id} failed: ${error.message ?: error::class.simpleName}")
                            Result.failure(error)
                        }
                    }
                }.awaitAll()
            }
            val nowMillis = now.toEpochMilliseconds()
            val events = results
                .flatMap { it.getOrNull().orEmpty() }
                .filter { event ->
                    when (event.state) {
                        LiveTvSportsState.Live -> true
                        LiveTvSportsState.Upcoming -> event.startMillis - nowMillis <= 48 * HOUR_MILLIS
                        LiveTvSportsState.Finished -> nowMillis - event.startMillis <= 18 * HOUR_MILLIS
                    }
                }
                .distinctBy { "${it.league.id}:${it.id}" }
                .sortedWith(liveTvSportsEventOrder)
            val allFailed = results.isNotEmpty() && results.all { it.isFailure }
            val failures = results.mapIndexedNotNull { index, result ->
                result.exceptionOrNull()?.let { "${liveTvSportsLeagues[index].label}: ${it.message ?: it::class.simpleName}" }
            }
            val rawCount = results.sumOf { it.getOrNull()?.size ?: 0 }
            InAppLogger.info(
                SPORTS_LOG_TAG,
                "ESPN refresh: ${events.size} shown, $rawCount parsed, ${failures.size} leagues failed, range=${range.range}",
            )
            val emptyReason = when {
                events.isNotEmpty() -> null
                failures.isNotEmpty() -> failures.take(3).joinToString("\n")
                else -> null
            }
            _uiState.value = LiveTvSportsUiState(
                isLoading = false,
                hasLoaded = true,
                errorMessage = emptyReason ?: if (allFailed) results.first().exceptionOrNull()?.message ?: "Network error" else null,
                events = if (allFailed) _uiState.value.events else events,
                updatedAtMillis = nowMillis,
            )
        }
    }

    private suspend fun fetchLeague(league: LiveTvSportsLeague, dates: LiveTvSportsDates): List<LiveTvSportsEvent> {
        val base = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/scoreboard"
        var lastError: Throwable? = null
        suspend fun attempt(url: String): List<LiveTvSportsEvent>? = try {
            fetchScoreboard(league, url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastError = error
            InAppLogger.warn(SPORTS_LOG_TAG, "ESPN ${league.id} ${error.message ?: error::class.simpleName} for $url")
            null
        }

        attempt("$base?dates=${dates.range}&limit=1000")?.takeIf { it.isNotEmpty() }?.let { return it }

        val perDay = mutableListOf<LiveTvSportsEvent>()
        var anyDaySucceeded = false
        for (day in dates.days) {
            attempt("$base?dates=$day")?.let { events ->
                anyDaySucceeded = true
                perDay += events
            }
        }
        if (perDay.isNotEmpty()) return perDay

        attempt(base)?.let { return it }
        if (anyDaySucceeded) return emptyList()
        throw lastError ?: IllegalStateException("No scoreboard response")
    }

    private suspend fun fetchScoreboard(league: LiveTvSportsLeague, url: String): List<LiveTvSportsEvent> {
        val text = httpGetTextWithHeaders(url, headers)
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val raw = root.arr("events")
        var firstFailure: String? = null
        val parsed = raw.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            runCatching { parseEvent(event, league) }
                .onFailure { if (firstFailure == null) firstFailure = it.message ?: it::class.simpleName }
                .getOrNull()
        }
        if (raw.isNotEmpty() && parsed.isEmpty()) {
            val reason = firstFailure?.let { failure -> " ($failure)" }.orEmpty()
            error("0 of " + raw.size + " events readable" + reason)
        }
        return parsed
    }

    private fun parseEvent(event: JsonObject, league: LiveTvSportsLeague): LiveTvSportsEvent? {
        val id = event.str("id") ?: return null
        val rawDate = event.str("date")
        val startMillis = rawDate?.let(::parseEspnDate) ?: error("unreadable date: " + rawDate)
        val statusType = (event.obj("status") ?: event.arr("competitions").firstObj()?.obj("status"))?.obj("type")
        val state = when (statusType?.str("state")) {
            "in" -> LiveTvSportsState.Live
            "post" -> LiveTvSportsState.Finished
            else -> LiveTvSportsState.Upcoming
        }
        val competitions = event.arr("competitions").mapNotNull { it as? JsonObject }
        val competition = if (league.id == "ufc") competitions.lastOrNull() else competitions.firstOrNull()
        val competitors = competition?.arr("competitors").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val team = item.obj("team")
            val athlete = item.obj("athlete")
            val name = team?.str("displayName") ?: athlete?.str("displayName") ?: return@mapNotNull null
            LiveTvSportsCompetitor(
                name = name,
                shortName = team?.str("shortDisplayName") ?: athlete?.str("shortName") ?: name,
                logoUrl = team?.str("logo") ?: athlete?.obj("flag")?.str("href"),
                score = item.str("score"),
                isHome = item.str("homeAway") == "home",
                isWinner = (item["winner"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }.sortedBy { if (it.isHome) 1 else 0 }
        val broadcasts = buildList {
            competitions.forEach { comp ->
                comp.arr("broadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.arr("names")?.forEach { name ->
                        (name as? JsonPrimitive)?.contentOrNull?.let(::add)
                    }
                }
                comp.arr("geoBroadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.obj("media")?.str("shortName")?.let(::add)
                }
            }
        }.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase)
        return LiveTvSportsEvent(
            id = id,
            league = league,
            name = event.str("name") ?: event.str("shortName") ?: competitors.joinToString(" vs ") { it.name },
            startMillis = startMillis,
            state = state,
            statusDetail = statusType?.str("shortDetail") ?: statusType?.str("detail").orEmpty(),
            competitors = competitors,
            broadcasts = broadcasts,
        )
    }
}

private const val HOUR_MILLIS = 60L * 60L * 1000L
private const val SPORTS_LOG_TAG = "LiveTvSports"

private val liveTvSportsEventOrder = Comparator<LiveTvSportsEvent> { a, b ->
    val rank = { event: LiveTvSportsEvent ->
        when (event.state) {
            LiveTvSportsState.Live -> 0
            LiveTvSportsState.Upcoming -> 1
            LiveTvSportsState.Finished -> 2
        }
    }
    val byRank = rank(a).compareTo(rank(b))
    when {
        byRank != 0 -> byRank
        a.state == LiveTvSportsState.Finished -> b.startMillis.compareTo(a.startMillis)
        else -> a.startMillis.compareTo(b.startMillis)
    }
}

internal class LiveTvSportsDates(val range: String, val days: List<String>)

/**
 * ESPN scoreboards are keyed by US Eastern calendar days. Covers yesterday through the day after
 * tomorrow, in Eastern time (fixed UTC-5; the one-hour DST error only shifts the window edges).
 */
private fun espnDates(nowMillis: Long): LiveTvSportsDates {
    val easternOffsetMillis = -5 * HOUR_MILLIS
    fun ymd(millis: Long): String {
        val date = Instant.fromEpochMilliseconds(millis + easternOffsetMillis).toLocalDateTime(TimeZone.UTC).date
        return "${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}"
    }
    val days = (-1..2).map { offset -> ymd(nowMillis + offset * 24 * HOUR_MILLIS) }
    return LiveTvSportsDates(range = "${days.first()}-${days.last()}", days = days)
}

private val espnDateRegex = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?""")

private fun parseEspnDate(value: String): Long? {
    val match = espnDateRegex.find(value) ?: return null
    val g = match.groupValues
    val year = g[1].toInt()
    val month = g[2].toInt()
    val day = g[3].toInt()
    val hour = g[4].toInt()
    val minute = g[5].toInt()
    val second = g.getOrNull(6)?.toIntOrNull() ?: 0
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = if (month > 2) month - 3 else month + 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097L + doe - 719468L
    return ((days * 24 + hour) * 60 + minute) * 60_000L + second * 1000L
}

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
private fun List<JsonElement>.firstObj() = firstOrNull() as? JsonObject

// ---------------------------------------------------------------------------------------------
// Channel matching
// ---------------------------------------------------------------------------------------------

private val channelNoiseTokens = setOf(
    "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "1080p", "720p", "50fps", "60fps",
    "raw", "vip", "backup", "live", "tv",
)

internal fun normalizeSportsName(value: String): String =
    value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in channelNoiseTokens }
        .joinToString(" ")

/**
 * Finds playlist channels that probably carry [event]: channels named after both teams (event
 * channels many providers create per match) rank first, then channels whose name matches one of
 * ESPN's broadcaster names as a whole-word sequence.
 */
internal fun matchSportsChannels(
    event: LiveTvSportsEvent,
    channels: List<Pair<LiveTvChannel, String>>,
    limit: Int = 8,
): List<LiveTvChannel> {
    val teamKeys = event.competitors
        .map { normalizeSportsName(it.shortName).ifBlank { normalizeSportsName(it.name) } }
        .filter { it.length >= 3 }
    val broadcastKeys = event.broadcasts.map(::normalizeSportsName).filter { it.length >= 3 }
    if (teamKeys.size < 2 && broadcastKeys.isEmpty()) return emptyList()
    return channels.asSequence()
        .mapNotNull { (channel, normalized) ->
            val padded = " $normalized "
            val score = when {
                teamKeys.size >= 2 && teamKeys.all { padded.contains(" $it ") } -> 3
                broadcastKeys.any { it == normalized } -> 2
                broadcastKeys.any { padded.contains(" $it ") } -> 1
                else -> 0
            }
            if (score > 0) channel to score else null
        }
        .sortedWith(compareByDescending<Pair<LiveTvChannel, Int>> { it.second }.thenBy { it.first.name.length })
        .take(limit)
        .map { it.first }
        .toList()
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
internal fun LiveTvSportsControls(
    allLabel: String,
    liveLabel: String,
    leagues: List<LiveTvSportsLeague>,
    hasLive: Boolean,
    selectedFilter: String,
    counts: Map<String, Int>,
    onFilterSelected: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            LiveTvPanelFilterChip(
                label = allLabel,
                selected = selectedFilter == LIVE_TV_SPORTS_FILTER_ALL,
                onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_ALL) },
                count = counts[LIVE_TV_SPORTS_FILTER_ALL],
            )
            if (hasLive) {
                LiveTvPanelFilterChip(
                    label = liveLabel,
                    selected = selectedFilter == LIVE_TV_SPORTS_FILTER_LIVE,
                    onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_LIVE) },
                    count = counts[LIVE_TV_SPORTS_FILTER_LIVE],
                )
            }
            leagues.forEach { league ->
                LiveTvPanelFilterChip(
                    label = league.label,
                    selected = selectedFilter == league.id,
                    onClick = { onFilterSelected(league.id) },
                    count = counts[league.id],
                )
            }
        }
    }
}

internal const val LIVE_TV_SPORTS_FILTER_ALL = "all"
internal const val LIVE_TV_SPORTS_FILTER_LIVE = "live"

internal fun LazyListScope.liveTvSportsList(
    events: List<LiveTvSportsEvent>,
    matchedChannels: Map<String, List<LiveTvChannel>>,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
) {
    items(events, key = { "sports:${it.league.id}:${it.id}" }) { event ->
        LiveTvSportsEventCard(
            event = event,
            channels = matchedChannels["${event.league.id}:${event.id}"].orEmpty(),
            nowMillis = nowMillis,
            onChannelClick = onChannelClick,
        )
    }
}

@Composable
private fun LiveTvSportsEventCard(
    event: LiveTvSportsEvent,
    channels: List<LiveTvChannel>,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { channels.firstOrNull()?.let(onChannelClick) },
        enabled = channels.isNotEmpty(),
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.league.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvSportsStatus(event = event, nowMillis = nowMillis)
            }
            if (event.competitors.size >= 2) {
                event.competitors.forEach { competitor ->
                    LiveTvSportsCompetitorRow(
                        competitor = competitor,
                        showScore = event.state != LiveTvSportsState.Upcoming,
                        dimmed = event.state == LiveTvSportsState.Finished &&
                            event.competitors.any { it.isWinner } && !competitor.isWinner,
                    )
                }
            } else {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (event.broadcasts.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.live_tv_sports_broadcast, event.broadcasts.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (channels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    channels.forEach { channel ->
                        LiveTvSportsChannelChip(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            } else if (event.state != LiveTvSportsState.Finished) {
                Text(
                    text = stringResource(Res.string.live_tv_sports_no_channels),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun LiveTvSportsStatus(event: LiveTvSportsEvent, nowMillis: Long) {
    val tokens = MaterialTheme.nuvio
    when (event.state) {
        LiveTvSportsState.Live -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.live_tv_sports_live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (event.statusDetail.isNotBlank()) {
                Text(
                    text = event.statusDetail,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }

        LiveTvSportsState.Upcoming -> Text(
            text = formatSportsStart(event.startMillis, nowMillis),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.accent,
            fontWeight = FontWeight.SemiBold,
        )

        LiveTvSportsState.Finished -> Text(
            text = event.statusDetail.ifBlank { formatSportsStart(event.startMillis, nowMillis) },
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
        )
    }
}

private fun formatSportsStart(startMillis: Long, nowMillis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val time = formatLiveTvEpgTime(startMillis)
    return if (start.date == today) {
        time
    } else {
        "${start.dayOfMonth.toString().padStart(2, '0')}/${start.monthNumber.toString().padStart(2, '0')} $time"
    }
}

@Composable
private fun LiveTvSportsCompetitorRow(
    competitor: LiveTvSportsCompetitor,
    showScore: Boolean,
    dimmed: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val textColor = if (dimmed) tokens.colors.textMuted else tokens.colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!competitor.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = competitor.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.SportsSoccer,
                    contentDescription = null,
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = competitor.name,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = if (competitor.isWinner) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showScore && !competitor.score.isNullOrBlank()) {
            Text(
                text = competitor.score,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LiveTvSportsChannelChip(
    channel: LiveTvChannel,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(tokens.colors.overlaySelected)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
