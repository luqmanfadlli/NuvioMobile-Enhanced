package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class LiquidGlassSurface {
    PLAYER,
    STREAM_FILTERS,
    STREAM_SELECTION,
    STREAM_RESULTS,
    DETAILS,
    DETAIL_HEADER,
    DETAIL_CHIPS,
    DETAIL_CONTENT_CARDS,
    DETAIL_PRIMARY_ACTION,
    SEARCH,
    LIBRARY,
    LIBRARY_SAVED_TYPE_FILTER,
    LIBRARY_SAVED_SORT_FILTER,
    LIBRARY_CLOUD_FILTERS,
    LIBRARY_CLOUD_SEARCH,
    LIBRARY_CLOUD_CARDS,
    LIBRARY_CALENDAR,
    SETTINGS,
    BOTTOM_SHEETS,
    DETAIL_TRAILER_SELECTOR,
    DETAIL_TRAILER_MENU,
    DETAIL_EPISODE_SEARCH,
    DETAIL_EPISODE_GO_TO,
    DETAIL_EPISODE_SORT,
    CATEGORY_HEADER_TITLE,
    CATEGORY_HEADER_BACK_BUTTON;

    val defaultWithHighlight: Boolean
        get() = this != DETAIL_HEADER

    val isLegacyGlass: Boolean
        get() = when (this) {
            PLAYER,
            DETAIL_HEADER,
            BOTTOM_SHEETS,
            STREAM_FILTERS,
            STREAM_SELECTION,
            STREAM_RESULTS -> true
            else -> false
        }

    val defaultWithShadow: Boolean
        get() = this == LIBRARY_CLOUD_CARDS

    val defaultMaterial: LiquidGlassMaterial
        get() = when (this) {
            PLAYER,
            DETAILS,
            DETAIL_PRIMARY_ACTION,
            DETAIL_TRAILER_SELECTOR,
            LIBRARY,
            CATEGORY_HEADER_BACK_BUTTON -> LiquidGlassMaterial.LIQUID
            else -> LiquidGlassMaterial.FROSTED
        }

    val supportsLens: Boolean
        get() = this != DETAIL_HEADER

    val supportsIconTint: Boolean
        get() = when (this) {
            PLAYER, DETAILS, DETAIL_HEADER, DETAIL_PRIMARY_ACTION, DETAIL_TRAILER_SELECTOR,
            DETAIL_EPISODE_SEARCH, CATEGORY_HEADER_BACK_BUTTON, SEARCH, LIBRARY,
            LIBRARY_SAVED_TYPE_FILTER, LIBRARY_SAVED_SORT_FILTER, LIBRARY_CLOUD_FILTERS,
            LIBRARY_CLOUD_SEARCH -> true
            STREAM_FILTERS, STREAM_SELECTION, STREAM_RESULTS, DETAIL_CHIPS,
            DETAIL_CONTENT_CARDS, LIBRARY_CLOUD_CARDS, DETAIL_TRAILER_MENU, DETAIL_EPISODE_GO_TO,
            DETAIL_EPISODE_SORT, CATEGORY_HEADER_TITLE, LIBRARY_CALENDAR, SETTINGS, BOTTOM_SHEETS -> false
        }

    val defaultEnabled: Boolean
        get() = when (this) {
            PLAYER,
            STREAM_FILTERS,
            STREAM_SELECTION,
            STREAM_RESULTS,
            DETAILS,
            DETAIL_HEADER,
            DETAIL_PRIMARY_ACTION,
            LIBRARY,
            LIBRARY_SAVED_TYPE_FILTER,
            LIBRARY_SAVED_SORT_FILTER,
            LIBRARY_CLOUD_FILTERS,
            LIBRARY_CLOUD_SEARCH,
            LIBRARY_CALENDAR,
            SEARCH,
            BOTTOM_SHEETS,
            DETAIL_TRAILER_SELECTOR,
            DETAIL_TRAILER_MENU,
            DETAIL_EPISODE_SEARCH,
            DETAIL_EPISODE_GO_TO,
            DETAIL_EPISODE_SORT,
            CATEGORY_HEADER_TITLE,
            CATEGORY_HEADER_BACK_BUTTON -> true
            DETAIL_CHIPS,
            DETAIL_CONTENT_CARDS,
            LIBRARY_CLOUD_CARDS,
            SETTINGS -> false
        }
}

internal fun effectiveLiquidGlassMaterial(
    surface: LiquidGlassSurface,
    material: LiquidGlassMaterial?,
    refractionSupported: Boolean,
    cornerRadiusPercent: Float?,
): LiquidGlassMaterial {
    val requested = material ?: surface.defaultMaterial
    return if (
        requested == LiquidGlassMaterial.CLEAR &&
        (!surface.supportsLens || !refractionSupported || cornerRadiusPercent == 0f)
    ) {
        LiquidGlassMaterial.LIQUID
    } else {
        requested
    }
}

@Serializable
internal data class LiquidGlassSurfaceOverrides(
    val enabled: Boolean? = null,
    val cornerRadiusPercent: Float? = null,
    val blurRadiusDp: Float? = null,
    val refractionHeightDp: Float? = null,
    val refractionAmountDp: Float? = null,
    val tintOpacity: Float? = null,
    val withVibrancy: Boolean? = null,
    val withHighlight: Boolean? = null,
    val withShadow: Boolean? = null,
    val chromaticAberration: Boolean? = null,
    val depthEffect: Boolean? = null,
    val defaultIconColorArgb: Int? = null,
    val material: LiquidGlassMaterial? = null,
) {
    fun sanitized(): LiquidGlassSurfaceOverrides = copy(
        material = material?.takeUnless { it == LiquidGlassMaterial.SOLID },
        cornerRadiusPercent = cornerRadiusPercent.inheritOrClamp(0f, 50f),
        blurRadiusDp = blurRadiusDp.inheritOrClamp(0f, 32f),
        refractionHeightDp = refractionHeightDp.inheritOrClamp(0f, 48f),
        refractionAmountDp = refractionAmountDp.inheritOrClamp(0f, 48f),
        tintOpacity = tintOpacity.inheritOrClamp(0f, 1f),
        defaultIconColorArgb = defaultIconColorArgb?.let { it or 0xFF000000.toInt() },
    )

    private fun Float?.inheritOrClamp(minimum: Float, maximum: Float): Float? =
        this?.takeIf { it.isFinite() }?.coerceIn(minimum, maximum)

    fun withGlassEffectFrom(shared: LiquidGlassSurfaceOverrides): LiquidGlassSurfaceOverrides = copy(
        enabled = shared.enabled,
        material = shared.material,
        blurRadiusDp = shared.blurRadiusDp,
        refractionHeightDp = shared.refractionHeightDp,
        refractionAmountDp = shared.refractionAmountDp,
        tintOpacity = shared.tintOpacity,
        withVibrancy = shared.withVibrancy,
        withHighlight = shared.withHighlight,
        withShadow = shared.withShadow,
        chromaticAberration = shared.chromaticAberration,
        depthEffect = shared.depthEffect,
    ).sanitized()
}

@Serializable
internal data class LiquidGlassSurfaceRecipe(
    val cornerRadiusPercent: Float,
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val tintOpacity: Float,
)

internal fun liquidGlassSurfaceRecipe(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
): LiquidGlassSurfaceRecipe {
    val safeOverrides = overrides.sanitized()
    val defaults = when (surface) {
        LiquidGlassSurface.PLAYER -> LiquidGlassSurfaceRecipe(50f, 10f, 8f, 12f, 0.05f)
        LiquidGlassSurface.STREAM_FILTERS -> LiquidGlassSurfaceRecipe(50f, 10f, 6f, 8f, 0.35f)
        LiquidGlassSurface.STREAM_SELECTION -> LiquidGlassSurfaceRecipe(50f, 14f, 8f, 12f, 0.38f)
        LiquidGlassSurface.STREAM_RESULTS -> LiquidGlassSurfaceRecipe(15f, 14f, 6f, 9f, 0.32f)
        LiquidGlassSurface.DETAILS -> LiquidGlassSurfaceRecipe(50f, 14f, 8f, 12f, 0.30f)
        LiquidGlassSurface.DETAIL_HEADER -> LiquidGlassSurfaceRecipe(0f, 16f, 0f, 0f, 0.55f)
        LiquidGlassSurface.DETAIL_CHIPS -> LiquidGlassSurfaceRecipe(25f, 10f, 4f, 6f, 0.32f)
        LiquidGlassSurface.DETAIL_CONTENT_CARDS -> LiquidGlassSurfaceRecipe(15f, 12f, 6f, 9f, 0.38f)
        LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> LiquidGlassSurfaceRecipe(50f, 14f, 6f, 9f, 0.35f)
        LiquidGlassSurface.DETAIL_TRAILER_SELECTOR -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.DETAIL_TRAILER_MENU -> LiquidGlassSurfaceRecipe(50f, 14f, 6f, 9f, 0.45f)
        LiquidGlassSurface.DETAIL_EPISODE_SEARCH -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.34f)
        LiquidGlassSurface.DETAIL_EPISODE_GO_TO -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.DETAIL_EPISODE_SORT -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.CATEGORY_HEADER_TITLE -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.25f)
        LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.25f)
        LiquidGlassSurface.SEARCH -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.LIBRARY -> LiquidGlassSurfaceRecipe(50f, 12f, 6f, 9f, 0.22f)
        LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.LIBRARY_CLOUD_FILTERS -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f)
        LiquidGlassSurface.LIBRARY_CLOUD_SEARCH -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.34f)
        LiquidGlassSurface.LIBRARY_CLOUD_CARDS -> LiquidGlassSurfaceRecipe(15f, 12f, 6f, 9f, 0.38f)
        LiquidGlassSurface.LIBRARY_CALENDAR -> LiquidGlassSurfaceRecipe(44f, 32f, 3f, 5f, 0.6f)
        LiquidGlassSurface.SETTINGS -> LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.42f)
        LiquidGlassSurface.BOTTOM_SHEETS -> LiquidGlassSurfaceRecipe(28f, 18f, 8f, 12f, 0.50f)
    }
    return defaults.copy(
        cornerRadiusPercent = safeOverrides.cornerRadiusPercent ?: defaults.cornerRadiusPercent,
        blurRadiusDp = safeOverrides.blurRadiusDp ?: defaults.blurRadiusDp,
        refractionHeightDp = safeOverrides.refractionHeightDp ?: defaults.refractionHeightDp,
        refractionAmountDp = safeOverrides.refractionAmountDp ?: defaults.refractionAmountDp,
        tintOpacity = safeOverrides.tintOpacity ?: defaults.tintOpacity,
    )
}

internal enum class LiquidGlassSurfaceGroup(val surfaces: List<LiquidGlassSurface>) {
    DETAIL_PAGE(
        listOf(
            LiquidGlassSurface.DETAIL_HEADER,
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.DETAIL_TRAILER_MENU,
            LiquidGlassSurface.DETAIL_CHIPS,
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
            LiquidGlassSurface.DETAIL_EPISODE_SORT,
            LiquidGlassSurface.DETAIL_CONTENT_CARDS,
        ),
    ),
    STREAM_PICKER(
        listOf(
            LiquidGlassSurface.STREAM_FILTERS,
            LiquidGlassSurface.STREAM_SELECTION,
            LiquidGlassSurface.STREAM_RESULTS,
        ),
    ),
    CATEGORY_HEADER(
        listOf(
            LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
        ),
    ),
    LIBRARY(
        listOf(
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            LiquidGlassSurface.LIBRARY_CALENDAR,
        ),
    ),
}

internal fun liquidGlassStandaloneSurfaces(
    surfaces: List<LiquidGlassSurface>,
    groups: List<LiquidGlassSurfaceGroup> = LiquidGlassSurfaceGroup.entries,
): List<LiquidGlassSurface> {
    val groupedSurfaces = groups.flatMap { it.surfaces }.toSet()
    return surfaces.filterNot { it in groupedSurfaces }
}

@Serializable
enum class LiquidGlassGeneralStyle {
    READABLE,
    BALANCED,
    VIVID,
}

@Serializable
internal data class LiquidGlassSurfacesSettings(
    val experimentalEnabled: Boolean = false,
    val generalStyle: LiquidGlassGeneralStyle = LiquidGlassGeneralStyle.READABLE,
    val player: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val streamFilters: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val streamSelection: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val details: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val detailHeader: LiquidGlassSurfaceOverrides = details,
    val detailChips: LiquidGlassSurfaceOverrides = details,
    val detailContentCards: LiquidGlassSurfaceOverrides = details,
    val detailPrimaryAction: LiquidGlassSurfaceOverrides = details,
    val detailTrailerSelector: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val detailTrailerMenu: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val detailEpisodeSearch: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val detailEpisodeGoTo: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val detailEpisodeSort: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val categoryHeaderTitle: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val categoryHeaderBackButton: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val streamResults: LiquidGlassSurfaceOverrides = streamSelection,
    val search: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val library: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val librarySavedTypeFilter: LiquidGlassSurfaceOverrides = library,
    val librarySavedSortFilter: LiquidGlassSurfaceOverrides = library,
    val libraryCloudFilters: LiquidGlassSurfaceOverrides = library,
    val libraryCloudSearch: LiquidGlassSurfaceOverrides = library,
    val libraryCloudCards: LiquidGlassSurfaceOverrides = library,
    val libraryCalendar: LiquidGlassSurfaceOverrides = library,
    val settings: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
    val bottomSheets: LiquidGlassSurfaceOverrides = LiquidGlassSurfaceOverrides(),
) {
    fun forSurface(surface: LiquidGlassSurface): LiquidGlassSurfaceOverrides = when (surface) {
        LiquidGlassSurface.PLAYER -> player
        LiquidGlassSurface.STREAM_FILTERS -> streamFilters
        LiquidGlassSurface.STREAM_SELECTION -> streamSelection
        LiquidGlassSurface.STREAM_RESULTS -> streamResults
        LiquidGlassSurface.DETAILS -> details
        LiquidGlassSurface.DETAIL_HEADER -> detailHeader
        LiquidGlassSurface.DETAIL_CHIPS -> detailChips
        LiquidGlassSurface.DETAIL_CONTENT_CARDS -> detailContentCards
        LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> detailPrimaryAction
        LiquidGlassSurface.DETAIL_TRAILER_SELECTOR -> detailTrailerSelector
        LiquidGlassSurface.DETAIL_TRAILER_MENU -> detailTrailerMenu
        LiquidGlassSurface.DETAIL_EPISODE_SEARCH -> detailEpisodeSearch
        LiquidGlassSurface.DETAIL_EPISODE_GO_TO -> detailEpisodeGoTo
        LiquidGlassSurface.DETAIL_EPISODE_SORT -> detailEpisodeSort
        LiquidGlassSurface.CATEGORY_HEADER_TITLE -> categoryHeaderTitle
        LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> categoryHeaderBackButton
        LiquidGlassSurface.SEARCH -> search
        LiquidGlassSurface.LIBRARY -> library
        LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER -> librarySavedTypeFilter
        LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER -> librarySavedSortFilter
        LiquidGlassSurface.LIBRARY_CLOUD_FILTERS -> libraryCloudFilters
        LiquidGlassSurface.LIBRARY_CLOUD_SEARCH -> libraryCloudSearch
        LiquidGlassSurface.LIBRARY_CLOUD_CARDS -> libraryCloudCards
        LiquidGlassSurface.LIBRARY_CALENDAR -> libraryCalendar
        LiquidGlassSurface.SETTINGS -> settings
        LiquidGlassSurface.BOTTOM_SHEETS -> bottomSheets
    }

    fun withSurface(
        surface: LiquidGlassSurface,
        overrides: LiquidGlassSurfaceOverrides,
    ): LiquidGlassSurfacesSettings {
        val safe = overrides.sanitized()
        return when (surface) {
            LiquidGlassSurface.PLAYER -> copy(player = safe)
            LiquidGlassSurface.STREAM_FILTERS -> copy(streamFilters = safe)
            LiquidGlassSurface.STREAM_SELECTION -> copy(streamSelection = safe)
            LiquidGlassSurface.STREAM_RESULTS -> copy(streamResults = safe)
            LiquidGlassSurface.DETAILS -> copy(details = safe)
            LiquidGlassSurface.DETAIL_HEADER -> copy(detailHeader = safe)
            LiquidGlassSurface.DETAIL_CHIPS -> copy(detailChips = safe)
            LiquidGlassSurface.DETAIL_CONTENT_CARDS -> copy(detailContentCards = safe)
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> copy(detailPrimaryAction = safe)
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR -> copy(detailTrailerSelector = safe)
            LiquidGlassSurface.DETAIL_TRAILER_MENU -> copy(detailTrailerMenu = safe)
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH -> copy(detailEpisodeSearch = safe)
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO -> copy(detailEpisodeGoTo = safe)
            LiquidGlassSurface.DETAIL_EPISODE_SORT -> copy(detailEpisodeSort = safe)
            LiquidGlassSurface.CATEGORY_HEADER_TITLE -> copy(categoryHeaderTitle = safe)
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> copy(categoryHeaderBackButton = safe)
            LiquidGlassSurface.SEARCH -> copy(search = safe)
            LiquidGlassSurface.LIBRARY -> copy(library = safe)
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER -> copy(librarySavedTypeFilter = safe)
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER -> copy(librarySavedSortFilter = safe)
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS -> copy(libraryCloudFilters = safe)
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH -> copy(libraryCloudSearch = safe)
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS -> copy(libraryCloudCards = safe)
            LiquidGlassSurface.LIBRARY_CALENDAR -> copy(libraryCalendar = safe)
            LiquidGlassSurface.SETTINGS -> copy(settings = safe)
            LiquidGlassSurface.BOTTOM_SHEETS -> copy(bottomSheets = safe)
        }
    }

    fun sanitized(): LiquidGlassSurfacesSettings = LiquidGlassSurfacesSettings(
        experimentalEnabled = experimentalEnabled,
        generalStyle = generalStyle,
        player = player.sanitized(),
        streamFilters = streamFilters.sanitized(),
        streamSelection = streamSelection.sanitized(),
        details = details.sanitized(),
        detailHeader = detailHeader.sanitized(),
        detailChips = detailChips.sanitized(),
        detailContentCards = detailContentCards.sanitized(),
        detailPrimaryAction = detailPrimaryAction.sanitized(),
        detailTrailerSelector = detailTrailerSelector.sanitized(),
        detailTrailerMenu = detailTrailerMenu.sanitized(),
        detailEpisodeSearch = detailEpisodeSearch.sanitized(),
        detailEpisodeGoTo = detailEpisodeGoTo.sanitized(),
        detailEpisodeSort = detailEpisodeSort.sanitized(),
        categoryHeaderTitle = categoryHeaderTitle.sanitized(),
        categoryHeaderBackButton = categoryHeaderBackButton.sanitized(),
        streamResults = streamResults.sanitized(),
        search = search.sanitized(),
        library = library.sanitized(),
        librarySavedTypeFilter = librarySavedTypeFilter.sanitized(),
        librarySavedSortFilter = librarySavedSortFilter.sanitized(),
        libraryCloudFilters = libraryCloudFilters.sanitized(),
        libraryCloudSearch = libraryCloudSearch.sanitized(),
        libraryCloudCards = libraryCloudCards.sanitized(),
        libraryCalendar = libraryCalendar.sanitized(),
        settings = settings.sanitized(),
        bottomSheets = bottomSheets.sanitized(),
    )
}

internal val LocalLiquidGlassPreviewOverrides = staticCompositionLocalOf<LiquidGlassSurfaceOverrides?> { null }

internal val LocalLiquidGlassPreviewSettings = staticCompositionLocalOf<LiquidGlassSurfacesSettings?> { null }

private val liquidGlassSettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun encodeLiquidGlassSurfaceSettings(settings: LiquidGlassSurfacesSettings): String =
    liquidGlassSettingsJson.encodeToString(settings.sanitized())

internal fun decodeLiquidGlassSurfaceSettings(raw: String?): LiquidGlassSurfacesSettings {
    if (raw.isNullOrBlank()) return LiquidGlassSurfacesSettings()
    return runCatching {
        liquidGlassSettingsJson.decodeFromString<LiquidGlassSurfacesSettings>(raw).sanitized()
    }.getOrElse { LiquidGlassSurfacesSettings() }
}

internal fun LiquidGlassGeneralStyle.baseOverrides(surface: LiquidGlassSurface): LiquidGlassSurfaceOverrides {
    val defaults = liquidGlassSurfaceRecipe(surface)
    return when (this) {
        LiquidGlassGeneralStyle.READABLE -> LiquidGlassSurfaceOverrides(
            material = LiquidGlassMaterial.FROSTED,
            blurRadiusDp = defaults.blurRadiusDp + 6f,
            tintOpacity = (defaults.tintOpacity * 1.5f + 0.12f).coerceAtMost(0.85f),
        )
        LiquidGlassGeneralStyle.BALANCED -> LiquidGlassSurfaceOverrides()
        LiquidGlassGeneralStyle.VIVID -> LiquidGlassSurfaceOverrides(
            material = LiquidGlassMaterial.LIQUID,
            tintOpacity = defaults.tintOpacity * 0.6f,
        )
    }.sanitized()
}

internal fun LiquidGlassSurfaceOverrides.over(base: LiquidGlassSurfaceOverrides): LiquidGlassSurfaceOverrides =
    LiquidGlassSurfaceOverrides(
        enabled = enabled ?: base.enabled,
        cornerRadiusPercent = cornerRadiusPercent ?: base.cornerRadiusPercent,
        blurRadiusDp = blurRadiusDp ?: base.blurRadiusDp,
        refractionHeightDp = refractionHeightDp ?: base.refractionHeightDp,
        refractionAmountDp = refractionAmountDp ?: base.refractionAmountDp,
        tintOpacity = tintOpacity ?: base.tintOpacity,
        withVibrancy = withVibrancy ?: base.withVibrancy,
        withHighlight = withHighlight ?: base.withHighlight,
        withShadow = withShadow ?: base.withShadow,
        chromaticAberration = chromaticAberration ?: base.chromaticAberration,
        depthEffect = depthEffect ?: base.depthEffect,
        defaultIconColorArgb = defaultIconColorArgb ?: base.defaultIconColorArgb,
        material = material ?: base.material,
    )

@Suppress("UNUSED_PARAMETER")
internal fun isLiquidGlassLegacyPassthrough(
    surface: LiquidGlassSurface,
    settings: LiquidGlassSurfacesSettings = LiquidGlassSettings.surfaceOverrides,
): Boolean = surface.isLegacyGlass

internal fun liquidGlassRuntimeOverrides(
    surface: LiquidGlassSurface,
    settings: LiquidGlassSurfacesSettings = LiquidGlassSettings.surfaceOverrides,
): LiquidGlassSurfaceOverrides {
    if (isLiquidGlassLegacyPassthrough(surface, settings)) return LiquidGlassSurfaceOverrides()
    if (!settings.experimentalEnabled) return LiquidGlassSurfaceOverrides(enabled = false)
    return settings.generalStyle.baseOverrides(surface).copy(enabled = true)
}

@Composable
private fun resolvedLiquidGlassOverrides(
    surface: LiquidGlassSurface,
    preview: Boolean,
): Pair<Boolean, LiquidGlassSurfaceOverrides> {
    val previewSettings = LocalLiquidGlassPreviewSettings.current
    val singlePreviewOverride = LocalLiquidGlassPreviewOverrides.current
    val isPreview = preview || previewSettings != null || singlePreviewOverride != null
    val overrides = if (isPreview) {
        previewSettings?.forSurface(surface)
            ?: singlePreviewOverride
            ?: liquidGlassRuntimeOverrides(surface)
    } else {
        liquidGlassRuntimeOverrides(surface)
    }
    return isPreview to overrides
}

internal fun shouldRenderLiquidGlassSurface(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
    preview: Boolean,
    masterEnabled: Boolean,
    platformSupported: Boolean = true,
): Boolean = (overrides.enabled ?: surface.defaultEnabled) &&
    (preview || (masterEnabled && platformSupported))

@Composable
internal fun liquidGlassShape(
    surface: LiquidGlassSurface,
    defaultShape: Shape,
    preview: Boolean = false,
): Shape {
    val (isPreview, overrides) = resolvedLiquidGlassOverrides(surface, preview)
    if (!isPreview && isLiquidGlassLegacyPassthrough(surface)) return defaultShape
    if (!shouldRenderLiquidGlassSurface(surface, overrides, isPreview, LiquidGlassSettings.enabled)) {
        return defaultShape
    }
    val recipe = liquidGlassSurfaceRecipe(surface, overrides)
    if (defaultShape === RectangleShape || defaultShape !is CornerBasedShape) return defaultShape
    return scaleLiquidGlassCornerShape(defaultShape, recipe.cornerRadiusPercent)
}

internal fun effectiveLiquidGlassCornerRadiusScale(percent: Float): Float =
    effectiveLiquidGlassCornerRadiusPercent(percent) / 50f

private data class ScaledLiquidGlassCornerSize(
    val original: CornerSize,
    private val scale: Float,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        original.toPx(shapeSize, density) * scale
}

private fun CornerSize.unscaledForLiquidGlass(): CornerSize =
    if (this is ScaledLiquidGlassCornerSize) original.unscaledForLiquidGlass() else this

internal fun effectiveLiquidGlassSurfaceShape(
    shape: Shape,
    cornerRadiusPercent: Float?,
    enabled: Boolean,
): Shape = if (enabled && cornerRadiusPercent != null) {
    scaleLiquidGlassCornerShape(shape, cornerRadiusPercent)
} else {
    shape
}

internal fun scaleLiquidGlassCornerShape(shape: Shape, percent: Float): Shape {
    val corners = shape as? CornerBasedShape ?: return shape
    val scale = effectiveLiquidGlassCornerRadiusScale(percent)
    val originalCorners = corners.copy(
        topStart = corners.topStart.unscaledForLiquidGlass(),
        topEnd = corners.topEnd.unscaledForLiquidGlass(),
        bottomEnd = corners.bottomEnd.unscaledForLiquidGlass(),
        bottomStart = corners.bottomStart.unscaledForLiquidGlass(),
    )
    if (scale >= 1f) return originalCorners
    if (scale <= 0f) {
        return originalCorners.copy(
            topStart = ScaledLiquidGlassCornerSize(originalCorners.topStart, 0f),
            topEnd = ScaledLiquidGlassCornerSize(originalCorners.topEnd, 0f),
            bottomEnd = ScaledLiquidGlassCornerSize(originalCorners.bottomEnd, 0f),
            bottomStart = ScaledLiquidGlassCornerSize(originalCorners.bottomStart, 0f),
        )
    }
    return originalCorners.copy(
        topStart = ScaledLiquidGlassCornerSize(originalCorners.topStart, scale),
        topEnd = ScaledLiquidGlassCornerSize(originalCorners.topEnd, scale),
        bottomEnd = ScaledLiquidGlassCornerSize(originalCorners.bottomEnd, scale),
        bottomStart = ScaledLiquidGlassCornerSize(originalCorners.bottomStart, scale),
    )
}

internal data class LiquidGlassLensParameters(
    val refractionHeightPx: Float,
    val refractionAmountPx: Float,
)

internal fun constrainLiquidGlassLensParameters(
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    minimumCornerRadiusPx: Float,
    minimumDimensionPx: Float,
): LiquidGlassLensParameters {
    val cornerLimit = minimumCornerRadiusPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val dimensionLimit = minimumDimensionPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeHeight = refractionHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeAmount = refractionAmountPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    return LiquidGlassLensParameters(
        refractionHeightPx = safeHeight.coerceAtMost(cornerLimit),
        refractionAmountPx = safeAmount.coerceAtMost(dimensionLimit),
    )
}

@Composable
internal fun Modifier.liquidGlassForSurface(
    surface: LiquidGlassSurface,
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp = 16.dp,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = refractionHeight * 1.6f,
    withVibrancy: Boolean = true,
    withHighlight: Boolean? = null,
    withShadow: Boolean = false,
    backdropOpacity: Float = 1f,
    fallbackBrush: Brush? = null,
    explicitBackdrop: Backdrop? = null,
    useExplicitBackdrop: Boolean = false,
    preview: Boolean = false,
): Modifier {
    val (isPreview, overrides) = resolvedLiquidGlassOverrides(surface, preview)
    if (!isPreview && isLiquidGlassLegacyPassthrough(surface)) {
        return if (useExplicitBackdrop) {
            val host = explicitBackdrop as? LiquidGlassBackdropHost
            if (host != null) {
                DisposableEffect(host) {
                    host.acquire()
                    onDispose { host.release() }
                }
            }
            liquidGlassSurface(
                backdrop = explicitBackdrop?.takeIf { (host?.isReady ?: true) },
                shape = shape,
                tint = tint,
                fallback = fallback,
                blurRadius = blurRadius,
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                withVibrancy = withVibrancy,
                withHighlight = withHighlight ?: true,
                withShadow = withShadow,
                fallbackBrush = fallbackBrush,
                backdropOpacity = backdropOpacity,
            )
        } else {
            this.clip(shape).ambientLiquidGlass(
                shape = shape,
                tint = tint,
                fallback = fallback,
                blurRadius = blurRadius,
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                withVibrancy = withVibrancy,
                withHighlight = withHighlight ?: true,
                withShadow = withShadow,
                fallbackBrush = fallbackBrush,
                backdropOpacity = backdropOpacity,
            )
        }
    }
    val recipe = liquidGlassSurfaceRecipe(surface, overrides)
    val materialStyle = LiquidGlassStyle(
        material = effectiveLiquidGlassMaterial(
            surface = surface,
            material = overrides.material,
            refractionSupported = isLiquidRefractionSupported(),
            cornerRadiusPercent = recipe.cornerRadiusPercent,
        ),
    )
    val enabled = shouldRenderLiquidGlassSurface(surface, overrides, isPreview, LiquidGlassSettings.enabled) &&
        materialStyle.usesBackdrop
    val effectiveShape = effectiveLiquidGlassSurfaceShape(
        shape = shape,
        cornerRadiusPercent = recipe.cornerRadiusPercent,
        enabled = enabled,
    )
    if (!enabled) {
        return if (fallbackBrush != null) {
            this.clip(effectiveShape).background(fallbackBrush, effectiveShape)
        } else {
            this.clip(effectiveShape).background(fallback, effectiveShape)
        }
    }

    val effectiveTint = if (tint.alpha > 0f) tint.copy(alpha = recipe.tintOpacity) else tint
    val effectiveBlur = if (materialStyle.usesBlur) recipe.blurRadiusDp.dp else 0.dp
    val effectiveRefractionHeight = if (materialStyle.usesLens && surface.supportsLens) {
        recipe.refractionHeightDp.dp
    } else {
        0.dp
    }
    val effectiveRefractionAmount = if (materialStyle.usesLens && surface.supportsLens) {
        recipe.refractionAmountDp.dp
    } else {
        0.dp
    }
    val effectiveWithHighlight = overrides.withHighlight ?: withHighlight ?: surface.defaultWithHighlight
    return if (useExplicitBackdrop) {
        val host = explicitBackdrop as? LiquidGlassBackdropHost
        if (host != null) {
            DisposableEffect(host) {
                host.acquire()
                onDispose { host.release() }
            }
        }
        liquidGlassSurface(
            backdrop = explicitBackdrop?.takeIf { (host?.isReady ?: true) },
            shape = effectiveShape,
            tint = effectiveTint,
            fallback = fallback,
            blurRadius = effectiveBlur,
            refractionHeight = effectiveRefractionHeight,
            refractionAmount = effectiveRefractionAmount,
            withVibrancy = overrides.withVibrancy ?: withVibrancy,
            withHighlight = effectiveWithHighlight,
            withShadow = overrides.withShadow ?: (withShadow || surface.defaultWithShadow),
            fallbackBrush = fallbackBrush,
            backdropOpacity = backdropOpacity,
            chromaticAberration = overrides.chromaticAberration ?: false,
            depthEffect = overrides.depthEffect ?: false,
            preview = isPreview,
            experimentalRecipe = true,
        ).let { this.clip(effectiveShape).then(it) }
    } else {
        this.clip(effectiveShape).ambientLiquidGlass(
            shape = effectiveShape,
            tint = effectiveTint,
            fallback = fallback,
            blurRadius = effectiveBlur,
            refractionHeight = effectiveRefractionHeight,
            refractionAmount = effectiveRefractionAmount,
            withVibrancy = overrides.withVibrancy ?: withVibrancy,
            withHighlight = effectiveWithHighlight,
            withShadow = overrides.withShadow ?: (withShadow || surface.defaultWithShadow),
            fallbackBrush = fallbackBrush,
            backdropOpacity = backdropOpacity,
            chromaticAberration = overrides.chromaticAberration ?: false,
            depthEffect = overrides.depthEffect ?: false,
            preview = isPreview,
            experimentalRecipe = true,
        )
    }
}

@Composable
internal fun liquidGlassIconTint(
    surface: LiquidGlassSurface,
    default: Color,
    preview: Boolean = false,
): Color {
    if (!surface.supportsIconTint) return default
    if (!preview && LocalLiquidGlassPreviewSettings.current == null &&
        LocalLiquidGlassPreviewOverrides.current == null && isLiquidGlassLegacyPassthrough(surface)
    ) {
        return default
    }
    val previewSettings = LocalLiquidGlassPreviewSettings.current
    val singlePreviewOverride = LocalLiquidGlassPreviewOverrides.current
    val isPreview = preview || previewSettings != null || singlePreviewOverride != null
    val previewOverrides = if (isPreview) {
        previewSettings?.forSurface(surface) ?: singlePreviewOverride
    } else {
        null
    }
    if (previewOverrides == null && !isPreview &&
        (!isLiquidGlassSupported() || !LiquidGlassSettings.enabled)
    ) {
        return default
    }
    val overrides = previewOverrides ?: liquidGlassRuntimeOverrides(surface)
    if (!shouldRenderLiquidGlassSurface(surface, overrides, isPreview, LiquidGlassSettings.enabled)) {
        return default
    }
    return overrides.defaultIconColorArgb?.let { Color(it).copy(alpha = default.alpha) } ?: default
}

@Composable
internal fun isLiquidGlassSurfaceEffectivelyEnabled(
    surface: LiquidGlassSurface,
    preview: Boolean = false,
    platformSupported: Boolean = true,
): Boolean {
    val (isPreview, overrides) = resolvedLiquidGlassOverrides(surface, preview)
    return shouldRenderLiquidGlassSurface(
        surface = surface,
        overrides = overrides,
        preview = isPreview,
        masterEnabled = LiquidGlassSettings.enabled,
        platformSupported = platformSupported,
    )
}

internal fun isLiquidGlassSurfacePreferenceEnabled(surface: LiquidGlassSurface): Boolean {
    val overrides = liquidGlassRuntimeOverrides(surface)
    return overrides.enabled ?: surface.defaultEnabled
}
