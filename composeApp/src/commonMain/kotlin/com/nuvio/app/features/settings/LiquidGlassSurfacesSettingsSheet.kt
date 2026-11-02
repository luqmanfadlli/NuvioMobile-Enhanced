package com.nuvio.app.features.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsAvailable
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassSwitch
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.shouldDrawNuvioInputFieldOutline
import com.nuvio.app.core.ui.LocalNuvioSheetBackdrop
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSheetSurface
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioSystemBackHandler
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.usesNativeNuvioBottomSheet
import com.nuvio.app.features.catalog.CatalogHeader
import com.nuvio.app.features.catalog.CatalogHeaderPreviewGrid
import com.nuvio.app.features.catalog.CATEGORY_HEADER_PREVIEW_SUBTITLE
import com.nuvio.app.features.catalog.CATEGORY_HEADER_PREVIEW_TITLE
import com.nuvio.app.features.details.components.DetailActions
import com.nuvio.app.features.details.components.DetailFloatingHeader
import com.nuvio.app.features.details.components.DetailGlassChipsPreview
import com.nuvio.app.features.details.components.DetailGlassContentCardPreview
import com.nuvio.app.features.details.components.DetailTrailersSection
import com.nuvio.app.features.details.components.EpisodeNavigatorBar
import com.nuvio.app.features.details.components.EpisodeGoToSheet
import com.nuvio.app.features.details.components.EpisodeNavigatorEmptyState
import com.nuvio.app.features.details.components.EpisodeSortSheet
import com.nuvio.app.features.details.components.filteredAndSortedForNavigator
import com.nuvio.app.features.details.components.rememberEpisodeNavigatorState
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaTrailer
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.downloads.DownloadsSettingsRepository
import com.nuvio.app.features.library.CloudLibrarySearchField
import com.nuvio.app.features.library.CloudLibraryToolbar
import com.nuvio.app.features.library.LibrarySavedControls
import com.nuvio.app.features.library.LibrarySourceSwitch
import com.nuvio.app.features.library.LibraryCalendarGlassPreview
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.features.library.LibraryViewMode
import com.nuvio.app.features.library.LibraryLayoutMode
import com.nuvio.app.features.library.LibrarySortOption
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.library.LibraryVerticalProjection
import com.nuvio.app.features.cloud.CloudLibraryUiState
import com.nuvio.app.features.cloud.CloudLibraryItemType
import com.nuvio.app.features.player.PlayerChapter
import com.nuvio.app.features.player.PlayerControlsShell
import com.nuvio.app.features.player.PlayerLayoutMetrics
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamSearchField
import com.nuvio.app.features.streams.ProviderFilterRow
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.ResumeBanner
import com.nuvio.app.features.streams.StreamCard
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamBadge
import com.nuvio.app.features.streams.StreamBadgePlacement
import com.nuvio.app.core.ui.glass.LiquidGlassMaterial
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceGroup
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceOverrides
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceRecipe
import com.nuvio.app.core.ui.glass.liquidGlassSurfaceRecipe
import com.nuvio.app.core.ui.glass.LiquidGlassSurfacesSettings
import com.nuvio.app.core.ui.glass.effectiveLiquidGlassMaterial
import com.nuvio.app.core.ui.glass.LocalLiquidGlassBackdrop
import com.nuvio.app.core.ui.glass.isLiquidGlassBackdropReady
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.LocalLiquidGlassPreviewOverrides
import com.nuvio.app.core.ui.glass.LocalLiquidGlassPreviewSettings
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.core.ui.glass.liquidGlassStandaloneSurfaces
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.library_title
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_material_header
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_material_clear
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_material_frosted
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_material_liquid
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_corner_radius
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_blur_radius
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_refraction_height
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_refraction_amount
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_tint_opacity
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_highlight
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_highlight_description
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_vibrancy
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_vibrancy_description
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_shadow
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_shadow_description
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_chromatic_aberration
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_chromatic_aberration_description
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_depth_effect
import nuvio.composeapp.generated.resources.settings_nav_bar_glass_depth_effect_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_custom_icon_color
import nuvio.composeapp.generated.resources.settings_liquid_glass_custom_icon_color_description
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.settings_liquid_glass_customize
import nuvio.composeapp.generated.resources.settings_liquid_glass_customize_action
import nuvio.composeapp.generated.resources.settings_liquid_glass_customize_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_layout
import nuvio.composeapp.generated.resources.settings_liquid_glass_suggested
import nuvio.composeapp.generated.resources.settings_liquid_glass_global_note
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_available_streams
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_cast_crew
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_choose_source
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_seasons_header
import nuvio.composeapp.generated.resources.cloud_library_playable_file_count
import nuvio.composeapp.generated.resources.cloud_library_status_ready
import nuvio.composeapp.generated.resources.collections_editor_tmdb_genre_comedy
import nuvio.composeapp.generated.resources.media_series
import nuvio.composeapp.generated.resources.settings_liquid_glass_reset
import nuvio.composeapp.generated.resources.settings_liquid_glass_done
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_ios_native_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_lens_requires_rounded_corners
import nuvio.composeapp.generated.resources.settings_liquid_glass_lens_requires_active_refraction
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_detail_page
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_detail_page_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_stream_picker
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_stream_picker_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_category_header
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_category_header_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_library
import nuvio.composeapp.generated.resources.settings_liquid_glass_preview_library_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_detail_page
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_detail_page_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_stream_picker
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_stream_picker_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_category_header
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_category_header_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_library
import nuvio.composeapp.generated.resources.settings_liquid_glass_group_library_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_library_background_title
import nuvio.composeapp.generated.resources.settings_liquid_glass_library_background_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_library_background_choose
import nuvio.composeapp.generated.resources.settings_liquid_glass_library_background_remove
import nuvio.composeapp.generated.resources.settings_liquid_glass_library_background_error
import nuvio.composeapp.generated.resources.settings_liquid_glass_link_effects
import nuvio.composeapp.generated.resources.settings_liquid_glass_link_effects_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_player
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_stream_selection
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_stream_filters
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_stream_results
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_header
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_chips
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_content_cards
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_primary_action

import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_trailer_selector
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_trailer_menu
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_episode_search
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_episode_go_to
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_detail_episode_sort
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_category_header_title
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_category_header_back_button
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_details
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_search
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_cloud_cards
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_calendar
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_cloud_search
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_cloud_filters
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_saved_sort_filter
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_library_saved_type_filter
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_settings
import nuvio.composeapp.generated.resources.compose_search_placeholder
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_root_appearance_description
import nuvio.composeapp.generated.resources.settings_appearance_section_display
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_bottom_sheets
import nuvio.composeapp.generated.resources.settings_liquid_glass_surface_bottom_sheets_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val LiquidGlassCustomizeSheetCardOverlayAlpha = 0.36f
private const val LiquidGlassCustomizeSheetCardBorderAlpha = 0.68f

/**
 * Apple recommends avoiding stacked Liquid Glass materials. Cards inside the glass sheet use a
 * quiet translucent fill instead of capturing and blurring the same backdrop a second time.
 */
internal fun shouldUseLiquidGlassCustomizeSheetCardOverlay(
    sheetGlassEnabled: Boolean,
    backdropReady: Boolean,
    nativeSheet: Boolean,
): Boolean = sheetGlassEnabled && backdropReady && !nativeSheet

private data class LiquidGlassCustomizeSheetCardStyle(
    val modifier: Modifier,
    val containerColor: Color,
    val borderColor: Color,
)

@Composable
private fun liquidGlassCustomizeSheetCardStyle(
    shape: Shape,
    fallbackColor: Color,
    fallbackBorderColor: Color,
): LiquidGlassCustomizeSheetCardStyle {
    val backdrop = LocalNuvioSheetBackdrop.current?.backdrop
    val useOverlay = shouldUseLiquidGlassCustomizeSheetCardOverlay(
        sheetGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
            surface = LiquidGlassSurface.BOTTOM_SHEETS,
            platformSupported = isLiquidGlassSupported(),
        ),
        backdropReady = backdrop?.isReady == true,
        nativeSheet = usesNativeNuvioBottomSheet,
    )
    if (!useOverlay || backdrop == null) {
        return LiquidGlassCustomizeSheetCardStyle(
            modifier = Modifier,
            containerColor = fallbackColor,
            borderColor = fallbackBorderColor,
        )
    }

    // The parent sheet already supplies real blur and refraction. A translucent dark film
    // separates the cards from that material without stacking another blur/lens pipeline.
    return LiquidGlassCustomizeSheetCardStyle(
        modifier = Modifier.background(fallbackColor.copy(alpha = LiquidGlassCustomizeSheetCardOverlayAlpha), shape),
        containerColor = Color.Transparent,
        borderColor = fallbackBorderColor.copy(alpha = fallbackBorderColor.alpha * LiquidGlassCustomizeSheetCardBorderAlpha),
    )
}

@Composable
internal fun LiquidGlassSurfaceSettingsDrafts(
    content: @Composable (LiquidGlassSurfacesSettings) -> Unit,
) {
    val settings by ThemeSettingsRepository.liquidGlassSurfaceSettings.collectAsStateWithLifecycle()
    content(settings)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LiquidGlassSurfacesSettingsSheet(
    isTablet: Boolean,
    settings: LiquidGlassSurfacesSettings,
    onSurfaceChanged: (LiquidGlassSurface, LiquidGlassSurfaceOverrides) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedSurfaceName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPreviewLayoutName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSurface = selectedSurfaceName?.let { name ->
        LiquidGlassSurface.entries.firstOrNull { it.name == name }
    }
    val selectedPreviewLayout = selectedPreviewLayoutName?.let { name ->
        LiquidGlassSurfaceGroup.entries.firstOrNull { it.name == name }
    }
    var linkGlassEffects by rememberSaveable(selectedPreviewLayoutName) { mutableStateOf(false) }
    val customizableSurfaces = remember(usesNativeNuvioBottomSheet) {
        if (usesNativeNuvioBottomSheet) {
            LiquidGlassSurface.entries.filter { it != LiquidGlassSurface.BOTTOM_SHEETS }
        } else {
            LiquidGlassSurface.entries
        }
    }
    val standaloneSurfaces = remember(customizableSurfaces) {
        liquidGlassStandaloneSurfaces(customizableSurfaces)
    }
    val listState = rememberLazyListState()
    val editorListState = rememberLazyListState()
    var savedRootListIndex by rememberSaveable { mutableStateOf(0) }
    var savedRootListScrollOffset by rememberSaveable { mutableStateOf(0) }
    var resetRootListOnNextOpen by rememberSaveable { mutableStateOf(true) }
    val isEditingSurface = selectedSurface != null || selectedPreviewLayout != null

    val captureRootListPosition: () -> Unit = {
        savedRootListIndex = listState.firstVisibleItemIndex
        savedRootListScrollOffset = listState.firstVisibleItemScrollOffset
    }
    val returnToRootList: () -> Unit = {
        selectedSurfaceName = null
        selectedPreviewLayoutName = null
        linkGlassEffects = false
    }

    // Keep the root list's exact item and pixel offset while editor content replaces its items.
    // Re-entering the root list restores that saved position; fully closing the popup marks the
    // next opening as a fresh session and starts from the top.
    LaunchedEffect(isEditingSurface, resetRootListOnNextOpen) {
        if (isEditingSurface) {
            editorListState.scrollToItem(index = 0, scrollOffset = 0)
        } else {
            val target = liquidGlassEditorScrollTarget(
                resetToTop = resetRootListOnNextOpen,
                savedIndex = savedRootListIndex,
                savedScrollOffset = savedRootListScrollOffset,
            )
            listState.scrollToItem(index = target.index, scrollOffset = target.scrollOffset)
            if (resetRootListOnNextOpen) resetRootListOnNextOpen = false
        }
    }

    val dismiss: () -> Unit = {
        // Persist before starting the dismiss animation, which can dispose the sheet immediately.
        onSave()
        scope.launch {
            dismissNuvioBottomSheet(sheetState) {
                selectedSurfaceName = null
                selectedPreviewLayoutName = null
                linkGlassEffects = false
                savedRootListIndex = 0
                savedRootListScrollOffset = 0
                resetRootListOnNextOpen = true
                onDismiss()
            }
        }
    }

    val previewCaptureOwner = LocalNuvioSheetBackdrop.current
    DisposableEffect(previewCaptureOwner) {
        previewCaptureOwner?.let { it.activeGlassPreviews += 1 }
        onDispose {
            previewCaptureOwner?.let {
                it.activeGlassPreviews = (it.activeGlassPreviews - 1).coerceAtLeast(0)
            }
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        dismissOnBackPress = !isEditingSurface,
    ) {
        NuvioSystemBackHandler(
            enabled = isEditingSurface,
            onBack = returnToRootList,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = if (isEditingSurface) editorListState else listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedSurface == null && selectedPreviewLayout == null) {
                item {
                    val introTokens = MaterialTheme.nuvio
                    val introCardShape = introTokens.shapes.card
                    val introCardGlass = liquidGlassCustomizeSheetCardStyle(
                        shape = introCardShape,
                        fallbackColor = introTokens.colors.surfaceCard,
                        fallbackBorderColor = introTokens.colors.borderStrong,
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTablet) 24.dp else 16.dp)
                            .then(introCardGlass.modifier),
                        shape = introCardShape,
                        color = introCardGlass.containerColor,
                        contentColor = introTokens.colors.textPrimary,
                        border = BorderStroke(
                            introTokens.borders.thin,
                            introCardGlass.borderColor,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.settings_liquid_glass_customize),
                                style = MaterialTheme.typography.titleLarge,
                                color = introTokens.colors.textPrimary,
                            )
                            Text(
                                text = stringResource(Res.string.settings_liquid_glass_customize_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = introTokens.colors.textSecondary,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(introTokens.colors.borderSubtle.copy(alpha = 0.55f)),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    modifier = Modifier.padding(top = 1.dp).size(18.dp),
                                    tint = introTokens.colors.textSecondary,
                                )
                                Text(
                                    text = stringResource(Res.string.settings_liquid_glass_global_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = introTokens.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
                LiquidGlassSurfaceGroup.entries.forEach { group ->
                    item(key = "liquid-glass-layout-${group.name}") {
                        val titleRes = when (group) {
                            LiquidGlassSurfaceGroup.DETAIL_PAGE -> Res.string.settings_liquid_glass_group_detail_page
                            LiquidGlassSurfaceGroup.STREAM_PICKER -> Res.string.settings_liquid_glass_group_stream_picker
                            LiquidGlassSurfaceGroup.CATEGORY_HEADER -> Res.string.settings_liquid_glass_group_category_header
                            LiquidGlassSurfaceGroup.LIBRARY -> Res.string.settings_liquid_glass_group_library
                        }
                        val descriptionRes = when (group) {
                            LiquidGlassSurfaceGroup.DETAIL_PAGE -> Res.string.settings_liquid_glass_group_detail_page_description
                            LiquidGlassSurfaceGroup.STREAM_PICKER -> Res.string.settings_liquid_glass_group_stream_picker_description
                            LiquidGlassSurfaceGroup.CATEGORY_HEADER -> Res.string.settings_liquid_glass_group_category_header_description
                            LiquidGlassSurfaceGroup.LIBRARY -> Res.string.settings_liquid_glass_group_library_description
                        }
                        val title = stringResource(titleRes)
                        val description = stringResource(descriptionRes)
                        LiquidGlassSurfaceGroupListItem(
                            group = group,
                            title = title,
                            description = description,
                            isTablet = isTablet,
                            onOpenPreview = {
                                captureRootListPosition()
                                selectedPreviewLayoutName = group.name
                                selectedSurfaceName = group.surfaces.firstOrNull { it in customizableSurfaces }?.name
                                linkGlassEffects = false
                            },
                        )
                    }
                }

                items(
                    items = standaloneSurfaces,
                    key = { surface -> "liquid-glass-surface-${surface.name}" },
                ) { surface ->
                    LiquidGlassSurfaceListItem(
                        surface = surface,
                        overrides = settings.forSurface(surface),
                        isTablet = isTablet,
                        onOpen = {
                            captureRootListPosition()
                            selectedPreviewLayoutName = null
                            selectedSurfaceName = surface.name
                        },
                        onToggle = { enabled ->
                            onSurfaceChanged(surface, settings.forSurface(surface).copy(enabled = enabled))
                        },
                    )
                }
            } else if (selectedSurface != null && selectedPreviewLayout != null) {
                stickyHeader(key = "liquid-glass-layout-preview-${selectedPreviewLayout.name}") {
                    LiquidGlassSurfaceGroupEditorHeader(
                        group = selectedPreviewLayout,
                        settings = settings,
                        selectedSurface = selectedSurface,
                        isTablet = isTablet,
                        onSelectSurface = { selectedSurfaceName = it.name },
                        onBack = returnToRootList,
                    )
                }
                item(key = "liquid-glass-layout-controls-${selectedPreviewLayout.name}-${selectedSurface.name}") {
                    LiquidGlassSurfaceEditorControls(
                        surface = selectedSurface,
                        overrides = settings.forSurface(selectedSurface),
                        isTablet = isTablet,
                        showLibraryCloudBackgroundControls = selectedSurface in liquidGlassLibraryCloudBackgroundSurfaces,
                        linkGlassEffects = linkGlassEffects,
                        onLinkGlassEffectsChanged = { link ->
                            if (link) {
                                val shared = resolvedGlassEffects(
                                    selectedSurface,
                                    settings.forSurface(selectedSurface),
                                )
                                selectedPreviewLayout.surfaces.forEach { target ->
                                    onSurfaceChanged(
                                        target,
                                        settings.forSurface(target).withGlassEffectFrom(shared),
                                    )
                                }
                            }
                            linkGlassEffects = link
                        },
                        onChanged = { changed ->
                            if (linkGlassEffects) {
                                val shared = resolvedGlassEffects(selectedSurface, changed)
                                selectedPreviewLayout.surfaces.forEach { target ->
                                    val base = if (target == selectedSurface) changed else settings.forSurface(target)
                                    onSurfaceChanged(target, base.withGlassEffectFrom(shared))
                                }
                            } else {
                                onSurfaceChanged(selectedSurface, changed)
                            }
                        },
                    )
                }
            } else if (selectedSurface != null) {
                stickyHeader(key = "liquid-glass-preview-${selectedSurface.name}") {
                    LiquidGlassSurfaceEditorHeader(
                        surface = selectedSurface,
                        overrides = settings.forSurface(selectedSurface),
                        settings = settings,
                        isTablet = isTablet,
                        onBack = returnToRootList,
                    )
                }
                item(key = "liquid-glass-controls-${selectedSurface.name}") {
                    LiquidGlassSurfaceEditorControls(
                        surface = selectedSurface,
                        overrides = settings.forSurface(selectedSurface),
                        isTablet = isTablet,
                        showLibraryCloudBackgroundControls = selectedSurface in liquidGlassLibraryCloudBackgroundSurfaces,
                        onChanged = { onSurfaceChanged(selectedSurface, it) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTablet) 24.dp else 16.dp)
                        .padding(top = 2.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = dismiss) {
                        Text(stringResource(Res.string.settings_liquid_glass_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassSurfaceListItem(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
    isTablet: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val enabled = overrides.enabled ?: surface.defaultEnabled
    val title = liquidGlassSurfaceTitle(surface)
    // Only top-level standalone areas get icons here; grouped control rows stay compact.
    val leadingIcon = when (surface) {
        LiquidGlassSurface.PLAYER -> Icons.Rounded.PlayCircle
        LiquidGlassSurface.SEARCH -> Icons.Rounded.Search
        LiquidGlassSurface.SETTINGS -> Icons.Rounded.Settings
        LiquidGlassSurface.BOTTOM_SHEETS -> Icons.Rounded.Layers
        else -> null
    }
    val iconSize = if (isTablet) 42.dp else 36.dp
    val iconGlyphSize = if (isTablet) 26.dp else 22.dp
    val cardShape = tokens.shapes.card
    val cardGlass = liquidGlassCustomizeSheetCardStyle(
        shape = cardShape,
        fallbackColor = tokens.colors.surfaceCard,
        fallbackBorderColor = tokens.colors.borderStrong,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 24.dp else 16.dp)
            .then(cardGlass.modifier),
        shape = cardShape,
        color = cardGlass.containerColor,
        contentColor = tokens.colors.textPrimary,
        border = BorderStroke(tokens.borders.thin, cardGlass.borderColor),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (leadingIcon != null) Arrangement.spacedBy(12.dp) else Arrangement.Start,
            ) {
                if (leadingIcon != null) {
                    Surface(
                        modifier = Modifier.size(iconSize),
                        color = tokens.colors.accent.copy(alpha = tokens.opacity.pressed),
                        shape = tokens.shapes.compactCard,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.size(iconGlyphSize),
                                tint = tokens.colors.textPrimary,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.textPrimary,
                    )
                }
                if (isNativeLiquidGlassControlsAvailable()) {
                    NuvioNativeLiquidGlassSwitch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        accessibilityLabel = title,
                    )
                } else {
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpen) {
                    Text(
                        text = stringResource(Res.string.settings_liquid_glass_customize_action),
                        color = tokens.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassSurfaceGroupListItem(
    group: LiquidGlassSurfaceGroup,
    title: String,
    description: String,
    isTablet: Boolean,
    onOpenPreview: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val icon = when (group) {
        LiquidGlassSurfaceGroup.DETAIL_PAGE -> Icons.Rounded.Movie
        LiquidGlassSurfaceGroup.STREAM_PICKER -> Icons.Rounded.Tune
        LiquidGlassSurfaceGroup.CATEGORY_HEADER -> Icons.Rounded.TextFields
        LiquidGlassSurfaceGroup.LIBRARY -> Icons.Rounded.VideoLibrary
    }
    val iconSize = if (isTablet) 42.dp else 36.dp
    val iconGlyphSize = if (isTablet) 26.dp else 22.dp
    val cardShape = tokens.shapes.card
    val cardGlass = liquidGlassCustomizeSheetCardStyle(
        shape = cardShape,
        fallbackColor = tokens.colors.surfaceCard,
        fallbackBorderColor = tokens.colors.borderStrong,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 24.dp else 16.dp)
            .then(cardGlass.modifier),
        shape = cardShape,
        color = cardGlass.containerColor,
        contentColor = tokens.colors.textPrimary,
        border = BorderStroke(tokens.borders.thin, cardGlass.borderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        heading()
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(iconSize),
                    color = tokens.colors.accent.copy(alpha = tokens.opacity.pressed),
                    shape = tokens.shapes.compactCard,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(iconGlyphSize),
                            tint = tokens.colors.textPrimary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenPreview) {
                    Text(text = stringResource(Res.string.settings_liquid_glass_preview_layout), color = tokens.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassSurfaceEditorHeader(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
    settings: LiquidGlassSurfacesSettings,
    isTablet: Boolean,
    onBack: () -> Unit,
) {
    val recipe = remember(surface, overrides) { liquidGlassSurfaceRecipe(surface, overrides) }
    val title = liquidGlassSurfaceTitle(surface)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nuvioSheetSurface(
                backdrop = LocalNuvioSheetBackdrop.current?.backdrop,
                shape = RectangleShape,
                containerColor = MaterialTheme.nuvio.colors.surfaceSheet,
                withHighlight = false,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 20.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = MaterialTheme.nuvio.colors.textPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_surface_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LiquidGlassSurfacePreview(
            surface = surface,
            recipe = recipe,
            overrides = overrides,
            settings = settings,
            isTablet = isTablet,
            modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
        )

    }
}

@Composable
private fun LiquidGlassSurfaceGroupEditorHeader(
    group: LiquidGlassSurfaceGroup,
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    isTablet: Boolean,
    onSelectSurface: (LiquidGlassSurface) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(
        when (group) {
            LiquidGlassSurfaceGroup.DETAIL_PAGE -> Res.string.settings_liquid_glass_preview_detail_page
            LiquidGlassSurfaceGroup.STREAM_PICKER -> Res.string.settings_liquid_glass_preview_stream_picker
            LiquidGlassSurfaceGroup.CATEGORY_HEADER -> Res.string.settings_liquid_glass_preview_category_header
            LiquidGlassSurfaceGroup.LIBRARY -> Res.string.settings_liquid_glass_preview_library
        },
    )
    val description = stringResource(
        when (group) {
            LiquidGlassSurfaceGroup.DETAIL_PAGE -> Res.string.settings_liquid_glass_preview_detail_page_description
            LiquidGlassSurfaceGroup.STREAM_PICKER -> Res.string.settings_liquid_glass_preview_stream_picker_description
            LiquidGlassSurfaceGroup.CATEGORY_HEADER -> Res.string.settings_liquid_glass_preview_category_header_description
            LiquidGlassSurfaceGroup.LIBRARY -> Res.string.settings_liquid_glass_preview_library_description
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nuvioSheetSurface(
                backdrop = LocalNuvioSheetBackdrop.current?.backdrop,
                shape = RectangleShape,
                containerColor = MaterialTheme.nuvio.colors.surfaceSheet,
                withHighlight = false,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 20.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = MaterialTheme.nuvio.colors.textPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when (group) {
            LiquidGlassSurfaceGroup.DETAIL_PAGE -> LiquidGlassDetailPagePreviewForStudio(
                settings = settings,
                selectedSurface = selectedSurface,
                isTablet = isTablet,
                onSelectSurface = onSelectSurface,
                modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
            )
            LiquidGlassSurfaceGroup.STREAM_PICKER -> LiquidGlassStreamPickerPreview(
                settings = settings,
                selectedSurface = selectedSurface,
                onSelectSurface = onSelectSurface,
                modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
            )
            LiquidGlassSurfaceGroup.CATEGORY_HEADER -> LiquidGlassCatalogHeaderPreview(
                settings = settings,
                selectedSurface = selectedSurface,
                onSelectSurface = onSelectSurface,
                modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
            )
            LiquidGlassSurfaceGroup.LIBRARY -> LiquidGlassLibraryPreview(
                settings = settings,
                selectedSurface = selectedSurface,
                onSelectSurface = onSelectSurface,
                modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun LiquidGlassDetailPagePreviewForStudio(
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    isTablet: Boolean,
    onSelectSurface: (LiquidGlassSurface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailScreenSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val downloadButtonEnabled by remember {
        DownloadsSettingsRepository.ensureLoaded()
        DownloadsSettingsRepository.showDownloadButton
    }.collectAsStateWithLifecycle()

    LiquidGlassDetailPagePreview(
        settings = settings,
        selectedSurface = selectedSurface,
        isTablet = isTablet,
        iconActionRow = detailScreenSettings.iconActionRow,
        downloadButtonEnabled = downloadButtonEnabled,
        onSelectSurface = onSelectSurface,
        modifier = modifier,
    )
}

@Composable
private fun LiquidGlassDetailPagePreview(
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    isTablet: Boolean,
    iconActionRow: Boolean,
    downloadButtonEnabled: Boolean,
    onSelectSurface: (LiquidGlassSurface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    val actionButtonHeight = if (isTablet) 56.dp else 52.dp
    val selectableActionSurfaces = remember(iconActionRow, downloadButtonEnabled) {
        liquidGlassDetailActionPreviewSelectableSurfaces(iconActionRow, downloadButtonEnabled)
    }
    val previewDownloadAction: (() -> Unit)? = if (downloadButtonEnabled) ({}) else null
    val meta = remember {
        MetaDetails(
            id = "liquid-glass-detail-preview",
            type = "series",
            name = "Nobody Wants This",
            releaseInfo = "2024",
            status = "Ongoing",
        )
    }
    val previewTrailers = remember { liquidGlassDetailPreviewTrailers() }
    val previewEpisodes = remember(meta.id) { liquidGlassDetailPreviewEpisodes() }
    val episodeNavigator = rememberEpisodeNavigatorState(meta.id)
    val isPreviewEpisodeWatched: (MetaVideo) -> Boolean = { it.episode == 1 }
    val shownPreviewEpisodes = remember(previewEpisodes, episodeNavigator.query, episodeNavigator.sort) {
        previewEpisodes.filteredAndSortedForNavigator(
            query = episodeNavigator.query,
            sort = episodeNavigator.sort,
            isWatched = { it.episode == 1 },
        )
    }
    var previewJumpTarget by remember(meta.id) { mutableStateOf<MetaVideo?>(null) }
    val previewEpisodeToShow = if (episodeNavigator.query.isNotBlank()) {
        shownPreviewEpisodes.firstOrNull()
    } else {
        previewJumpTarget ?: shownPreviewEpisodes.firstOrNull()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(326.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        // The background source is deliberately local and deterministic. Production Nuvio controls
        // below use the real component tree over it, so no catalogue/network data is required.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A202A),
                                Color(0xFF6A4D58),
                                Color(0xFF302533),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 58.dp, y = (-36).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xD6D7A8B8), Color(0x8A7A596D), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-58).dp, y = 70.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xC86B9AB3), Color(0x67485E79), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )

        }

        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdrop,
            LocalLiquidGlassPreviewSettings provides settings,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.72f)),
                            ),
                        ),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Nobody Wants This",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "2024  •  ${stringResource(Res.string.media_series)}  •  ${stringResource(Res.string.collections_editor_tmdb_genre_comedy)}",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GlassPreviewSelectionGutter),
                ) {
                    DetailActions(
                        playLabel = stringResource(Res.string.action_play),
                        playEnabled = true,
                        isSaved = false,
                        isWatched = false,
                        isTablet = isTablet,
                        shuffleEnabled = false,
                        onPlayClick = {},
                        onPlayLongClick = null,
                        onShuffleClick = null,
                        onWatchedClick = {},
                        onSaveClick = {},
                        onSaveLongClick = null,
                        onDownloadClick = previewDownloadAction,
                        onPlayFromStartClick = null,
                        onPlayExternallyClick = {},
                        iconActionRow = iconActionRow,
                        previewGlassAll = true,
                    )
                    // Hit targets follow the production action layout. Play is accent-filled;
                    // DETAIL_PRIMARY_ACTION belongs to the separate Download CTA shown only when
                    // the icon action row is disabled.
                    Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        if (iconActionRow) {
                            Box(modifier = Modifier.fillMaxWidth().height(actionButtonHeight + 12.dp))
                            if (LiquidGlassSurface.DETAILS in selectableActionSurfaces) {
                                GlassPreviewHotspot(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(actionButtonHeight + GlassPreviewSelectionGutter * 2)
                                        .offset(y = -GlassPreviewSelectionGutter),
                                    surface = LiquidGlassSurface.DETAILS,
                                    selectedSurface = selectedSurface,
                                    onClick = { onSelectSurface(LiquidGlassSurface.DETAILS) },
                                )
                            }
                        } else {
                            if (LiquidGlassSurface.DETAILS in selectableActionSurfaces) {
                                Box(modifier = Modifier.fillMaxWidth().height(actionButtonHeight + GlassPreviewSelectionGutter * 2)) {
                                    GlassPreviewHotspot(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(actionButtonHeight + GlassPreviewSelectionGutter * 2)
                                            .offset(y = -GlassPreviewSelectionGutter),
                                        surface = LiquidGlassSurface.DETAILS,
                                        selectedSurface = selectedSurface,
                                        shape = CircleShape,
                                        onClick = { onSelectSurface(LiquidGlassSurface.DETAILS) },
                                    )
                                }
                            }
                            if (LiquidGlassSurface.DETAIL_PRIMARY_ACTION in selectableActionSurfaces) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(
                                            if (LiquidGlassSurface.DETAILS in selectableActionSurfaces) {
                                                12.dp - GlassPreviewSelectionGutter * 2
                                            } else {
                                                12.dp
                                            },
                                        ),
                                )
                                GlassPreviewHotspot(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(actionButtonHeight + GlassPreviewSelectionGutter * 2)
                                        .offset(y = -GlassPreviewSelectionGutter),
                                    surface = LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
                                    selectedSurface = selectedSurface,
                                    shape = RoundedCornerShape(40.dp),
                                    onClick = { onSelectSurface(LiquidGlassSurface.DETAIL_PRIMARY_ACTION) },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_liquid_glass_preview_cast_crew),
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("KR", "AD", "JM", "TM").forEachIndexed { index, initials ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        listOf(Color(0xFF927B8E), Color(0xFF6E8C99), Color(0xFFAA866E), Color(0xFF75836C))[index],
                                        CircleShape,
                                    ),
                            )
                            Text(initials, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                DetailTrailersSection(
                    trailers = previewTrailers,
                    onTrailerClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    showHeader = true,
                    onPreviewSurfaceSelected = onSelectSurface,
                    selectedPreviewSurface = selectedSurface,
                    previewMenuInitiallyExpanded = selectedSurface == LiquidGlassSurface.DETAIL_TRAILER_MENU,
                )

                Text(
                    text = stringResource(Res.string.settings_liquid_glass_preview_seasons_header),
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    DetailGlassChipsPreview(
                        modifier = Modifier.fillMaxWidth().padding(GlassPreviewSelectionGutter),
                    )
                    GlassPreviewHotspot(
                        modifier = Modifier.fillMaxSize(),
                        surface = LiquidGlassSurface.DETAIL_CHIPS,
                        selectedSurface = selectedSurface,
                        onClick = { onSelectSurface(LiquidGlassSurface.DETAIL_CHIPS) },
                    )
                }

                EpisodeNavigatorBar(
                    state = episodeNavigator,
                    shownCount = shownPreviewEpisodes.size,
                    totalCount = previewEpisodes.size,
                    onSubmitNumber = { number ->
                        previewEpisodes.firstOrNull { it.episode == number }?.let { episode ->
                            previewJumpTarget = episode
                            episodeNavigator.query = ""
                        }
                    },
                    onPreviewSurfaceSelected = { surface ->
                        if (surface == LiquidGlassSurface.DETAIL_EPISODE_SEARCH ||
                            surface == LiquidGlassSurface.DETAIL_EPISODE_GO_TO ||
                            surface == LiquidGlassSurface.DETAIL_EPISODE_SORT
                        ) {
                            previewJumpTarget = null
                        }
                        onSelectSurface(surface)
                    },
                    selectedPreviewSurface = selectedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                )
                if (episodeNavigator.goToSheetVisible) {
                    EpisodeGoToSheet(
                        seasonLabel = "Season 1",
                        episodes = previewEpisodes,
                        isWatched = isPreviewEpisodeWatched,
                        onPick = { episode -> previewJumpTarget = episode },
                        onDismiss = { episodeNavigator.goToSheetVisible = false },
                    )
                }
                if (episodeNavigator.sortSheetVisible) {
                    EpisodeSortSheet(
                        current = episodeNavigator.sort,
                        onSelect = { episodeNavigator.sort = it },
                        onDismiss = { episodeNavigator.sortSheetVisible = false },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    if (shownPreviewEpisodes.isEmpty()) {
                        EpisodeNavigatorEmptyState(
                            query = episodeNavigator.query,
                            onClear = { episodeNavigator.query = "" },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        DetailGlassContentCardPreview(
                            modifier = Modifier.fillMaxWidth().padding(GlassPreviewSelectionGutter),
                            video = previewEpisodeToShow,
                        )
                    }
                    GlassPreviewHotspot(
                        modifier = Modifier.fillMaxSize(),
                        surface = LiquidGlassSurface.DETAIL_CONTENT_CARDS,
                        selectedSurface = selectedSurface,
                        onClick = { onSelectSurface(LiquidGlassSurface.DETAIL_CONTENT_CARDS) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.TopCenter),
            ) {
                DetailFloatingHeader(
                    meta = meta,
                    isSaved = false,
                    progress = 1f,
                    onBack = {},
                    onToggleSaved = {},
                    modifier = Modifier.fillMaxWidth(),
                    glassBackdrop = backdrop,
                    previewGlass = true,
                )
                GlassPreviewHotspot(
                    modifier = Modifier.fillMaxSize(),
                    surface = LiquidGlassSurface.DETAIL_HEADER,
                    selectedSurface = selectedSurface,
                    shape = RectangleShape,
                    onClick = { onSelectSurface(LiquidGlassSurface.DETAIL_HEADER) },
                )
            }
        }
    }
}

private fun liquidGlassDetailPreviewTrailers(): List<MetaTrailer> = listOf(
    MetaTrailer(
        id = "liquid-glass-trailer-preview",
        key = "Xn2PsXD8m4A",
        name = "Official Trailer",
        site = "YouTube",
        type = "Trailer",
        official = true,
        publishedAt = "2024-08-29",
        displayName = "Trailer",
    ),
)

private fun liquidGlassDetailPreviewEpisodes(): List<MetaVideo> = listOf(
    MetaVideo(
        id = "glass-preview-s1e1",
        title = "Pilot",
        released = "2024-09-26",
        season = 1,
        episode = 1,
        overview = "Fresh off another unsuccessful date, Joanne sashays into a friend's party and meets Noah.",
        runtime = 27,
        tmdbRating = 7.7,
    ),
    MetaVideo(
        id = "glass-preview-s1e2",
        title = "A Shiksa Walks into a Synagogue",
        released = "2024-09-26",
        season = 1,
        episode = 2,
        overview = "Joanne and Noah discover that dating across very different worlds takes some getting used to.",
        runtime = 25,
        tmdbRating = 7.6,
    ),
    MetaVideo(
        id = "glass-preview-s1e3",
        title = "Either Aura",
        released = "2024-09-26",
        season = 1,
        episode = 3,
        overview = "A new relationship brings new questions about identity, family and expectations.",
        runtime = 26,
        tmdbRating = 7.5,
    ),
    MetaVideo(
        id = "glass-preview-s1e4",
        title = "The Ick",
        released = "2024-09-26",
        season = 1,
        episode = 4,
        overview = "Small differences become harder to ignore as their relationship develops.",
        runtime = 25,
        tmdbRating = 7.4,
    ),
    MetaVideo(
        id = "glass-preview-s1e5",
        title = "My Friend Joanne",
        released = "2024-09-26",
        season = 1,
        episode = 5,
        overview = "Friends and family have plenty to say about the new couple.",
        runtime = 27,
        tmdbRating = 7.6,
    ),
    MetaVideo(
        id = "glass-preview-s1e6",
        title = "The Other Woman",
        released = "2024-09-26",
        season = 1,
        episode = 6,
        overview = "Joanne and Noah try to navigate the complications of a growing romance.",
        runtime = 26,
        tmdbRating = 7.3,
    ),
    MetaVideo(
        id = "glass-preview-s1e7",
        title = "WAGS",
        released = "2024-09-26",
        season = 1,
        episode = 7,
        overview = "An unexpected gathering puts their assumptions to the test.",
        runtime = 27,
        tmdbRating = 7.6,
    ),
    MetaVideo(
        id = "glass-preview-s1e8",
        title = "Rebecca's Box",
        released = "2024-09-26",
        season = 1,
        episode = 8,
        overview = "The relationship reaches a moment that calls for honesty.",
        runtime = 27,
        tmdbRating = 7.8,
    ),
    MetaVideo(
        id = "glass-preview-s1e9",
        title = "My Jewish Mom",
        released = "2024-09-26",
        season = 1,
        episode = 9,
        overview = "Family expectations complicate the couple's plans.",
        runtime = 26,
        tmdbRating = 7.7,
    ),
    MetaVideo(
        id = "glass-preview-s1e10",
        title = "Bat Mitzvah Crashers",
        released = "2024-09-26",
        season = 1,
        episode = 10,
        overview = "Joanne and Noah consider what the future might look like together.",
        runtime = 28,
        tmdbRating = 7.9,
    ),
)

private val GlassPreviewSelectionGutter = 4.dp

@Composable
private fun LiquidGlassStreamPickerPreview(
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    onSelectSurface: (LiquidGlassSurface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember { liquidGlassPreviewStreamGroups() }
    val previewStreams = remember { groups.flatMap { it.streams }.take(2) }
    val backdrop = rememberNuvioBackdrop()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(326.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF17212F), Color(0xFF334454), Color(0xFF1B1A24), MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(224.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-48).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xD8F2B76F), Color(0x827F67E8), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-54).dp, y = 66.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xCF58DACA), Color(0x7F7B65D7), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdrop,
            LocalLiquidGlassPreviewSettings provides settings,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = "Nobody Wants This",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_preview_choose_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Box(modifier = Modifier.fillMaxWidth().height(48.dp + GlassPreviewSelectionGutter * 2)) {
                    ProviderFilterRow(
                        groups = groups,
                        selectedFilter = "preview:torrentio",
                        onFilterSelected = {},
                        onRefresh = {},
                        modifier = Modifier.fillMaxSize().padding(GlassPreviewSelectionGutter),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp),
                        spacing = 6.dp,
                        preview = true,
                    )
                    GlassPreviewHotspot(
                        modifier = Modifier.fillMaxSize(),
                        surface = LiquidGlassSurface.STREAM_FILTERS,
                        selectedSurface = selectedSurface,
                        onClick = { onSelectSurface(LiquidGlassSurface.STREAM_FILTERS) },
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(100.dp + GlassPreviewSelectionGutter * 2)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(GlassPreviewSelectionGutter),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ResumeBanner(positionMs = null, progressFraction = 0.42f, modifier = Modifier.fillMaxWidth(), preview = true)
                        StreamSearchField(
                            query = "",
                            onQueryChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            preview = true,
                            nativeGlassEnabled = selectedSurface == LiquidGlassSurface.STREAM_SELECTION &&
                                isNativeLiquidGlassControlsAvailable() &&
                                isLiquidGlassSurfaceEffectivelyEnabled(
                                    LiquidGlassSurface.STREAM_SELECTION,
                                    preview = true,
                                    platformSupported = true,
                                ),
                        )
                    }
                    GlassPreviewHotspot(
                        modifier = Modifier.fillMaxSize(),
                        surface = LiquidGlassSurface.STREAM_SELECTION,
                        selectedSurface = selectedSurface,
                        onClick = { onSelectSurface(LiquidGlassSurface.STREAM_SELECTION) },
                    )
                }

                Text(
                    text = stringResource(Res.string.settings_liquid_glass_preview_available_streams),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().height(170.dp + GlassPreviewSelectionGutter * 2)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(GlassPreviewSelectionGutter),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        previewStreams.forEach { stream ->
                            StreamCard(
                                stream = stream,
                                enabled = true,
                                appendInstantServiceToDefaultName = false,
                                showFileSizeBadges = true,
                                showAddonLogo = false,
                                badgePlacement = StreamBadgePlacement.BOTTOM,
                                onClick = {},
                                preview = true,
                            )
                        }
                    }
                    GlassPreviewHotspot(
                        modifier = Modifier.fillMaxSize(),
                        surface = LiquidGlassSurface.STREAM_RESULTS,
                        selectedSurface = selectedSurface,
                        onClick = { onSelectSurface(LiquidGlassSurface.STREAM_RESULTS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPreviewHotspot(
    modifier: Modifier = Modifier,
    surface: LiquidGlassSurface,
    selectedSurface: LiquidGlassSurface,
    shape: Shape = RoundedCornerShape(8.dp),
    onClick: () -> Unit,
) {
    val selected = selectedSurface == surface
    val surfaceTitle = liquidGlassSurfaceTitle(surface)
    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = surfaceTitle
            }
            .clickable(
                onClickLabel = surfaceTitle,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

private fun liquidGlassPreviewStreamGroups(): List<AddonStreamGroup> = listOf(
    AddonStreamGroup(
        addonName = "Torrentio",
        addonId = "preview:torrentio",
        streams = listOf(
            StreamItem(
                name = "2160p | Cached | Torrentio",
                description = "WEB-DL • HEVC • 1.1 GB",
                addonName = "Torrentio",
                addonId = "preview:torrentio",
                streamType = "series",
            ),
        ),
    ),
    AddonStreamGroup(
        addonName = "Comet",
        addonId = "preview:comet",
        streams = listOf(
            StreamItem(
                name = "1080p | Comet",
                description = "WEB-DL • AVC • 473 MB",
                addonName = "Comet",
                addonId = "preview:comet",
                streamType = "series",
            ),
        ),
    ),
)

@Composable
private fun LiquidGlassSurfaceEditorControls(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
    isTablet: Boolean,
    showLibraryCloudBackgroundControls: Boolean = false,
    linkGlassEffects: Boolean = false,
    onLinkGlassEffectsChanged: ((Boolean) -> Unit)? = null,
    onChanged: (LiquidGlassSurfaceOverrides) -> Unit,
) {
    val title = liquidGlassSurfaceTitle(surface)
    val enabled = overrides.enabled ?: surface.defaultEnabled
    val nativeControlsAvailable = isNativeLiquidGlassControlsAvailable()
    val refractionSupported = isLiquidRefractionSupported()
    val recipe = remember(surface, overrides) { liquidGlassSurfaceRecipe(surface, overrides) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showLibraryCloudBackgroundControls) {
            // Keep the profile image subscription and native picker out of unrelated surface editors.
            val cloudBackgroundImageUri by remember {
                ThemeSettingsRepository.ensureLoaded()
                ThemeSettingsRepository.customLibraryCloudBackgroundImageUri
            }.collectAsStateWithLifecycle()
            var cloudBackgroundImageError by rememberSaveable(surface.name) { mutableStateOf(false) }
            // Saving is transient; don't restore a stale disabled state after process recreation.
            var isSavingCloudBackground by remember(surface.name) { mutableStateOf(false) }
            val cloudBackgroundScope = rememberCoroutineScope()
            val cloudBackgroundImagePicker = rememberSettingsBackgroundImagePicker(
                onImageSelected = { bytes ->
                    val targetProfileId = ProfileRepository.activeProfileId
                    cloudBackgroundImageError = false
                    isSavingCloudBackground = true
                    cloudBackgroundScope.launch {
                        val saved = try {
                            ThemeSettingsRepository.saveCustomLibraryCloudBackgroundImageForStudio(bytes, targetProfileId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                        isSavingCloudBackground = false
                        if (ProfileRepository.activeProfileId == targetProfileId) {
                            cloudBackgroundImageError = !saved
                        }
                    }
                },
                onError = { cloudBackgroundImageError = true },
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_library_background_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_library_background_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = !isSavingCloudBackground,
                        onClick = { cloudBackgroundImagePicker.launch() },
                    ) {
                        Text(stringResource(Res.string.settings_liquid_glass_library_background_choose))
                    }
                    if (cloudBackgroundImageUri != null) {
                        TextButton(
                            enabled = !isSavingCloudBackground,
                            onClick = {
                                val targetProfileId = ProfileRepository.activeProfileId
                                cloudBackgroundImageError = false
                                isSavingCloudBackground = true
                                cloudBackgroundScope.launch {
                                    val cleared = try {
                                        ThemeSettingsRepository.clearCustomLibraryCloudBackgroundImageForStudio(targetProfileId)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    isSavingCloudBackground = false
                                    if (ProfileRepository.activeProfileId == targetProfileId) {
                                        cloudBackgroundImageError = !cleared
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.settings_liquid_glass_library_background_remove))
                        }
                    }
                }
                if (cloudBackgroundImageError) {
                    Text(
                        text = stringResource(Res.string.settings_liquid_glass_library_background_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            NuvioBottomSheetDivider()
        }
        if (!nativeControlsAvailable) {
            onLinkGlassEffectsChanged?.let { onLinkChanged ->
                LiquidGlassSettingsSwitchRow(
                    title = stringResource(Res.string.settings_liquid_glass_link_effects),
                    description = stringResource(Res.string.settings_liquid_glass_link_effects_description),
                    checked = linkGlassEffects,
                    isTablet = isTablet,
                    onCheckedChange = onLinkChanged,
                )
            }
        }
        LiquidGlassSettingsSwitchRow(
            title = title,
            description = stringResource(
                if (surface == LiquidGlassSurface.BOTTOM_SHEETS) {
                    Res.string.settings_liquid_glass_surface_bottom_sheets_description
                } else {
                    Res.string.settings_liquid_glass_surface_description
                },
            ),
            checked = enabled,
            isTablet = isTablet,
            onCheckedChange = { onChanged(overrides.copy(enabled = it)) },
        )

        if (nativeControlsAvailable) {
            if (enabled) {
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_ios_native_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        } else {
            AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val effectiveCornerRadiusPercent =
                    overrides.cornerRadiusPercent ?: recipe.cornerRadiusPercent
                val activeMaterial = effectiveLiquidGlassMaterial(
                    surface = surface,
                    material = overrides.material,
                    refractionSupported = refractionSupported,
                    cornerRadiusPercent = effectiveCornerRadiusPercent,
                )
                val materialStyle = LiquidGlassStyle(material = activeMaterial)
                val canSelectClear = refractionSupported &&
                    surface.supportsLens &&
                    effectiveCornerRadiusPercent > 0f
                val materialOptions = listOf(
                    LiquidGlassMaterial.LIQUID,
                    LiquidGlassMaterial.FROSTED,
                ) + if (canSelectClear) listOf(LiquidGlassMaterial.CLEAR) else emptyList()
                val lensControlsAvailable = canSelectClear && materialStyle.usesLens
                val effectiveRefractionHeight = overrides.refractionHeightDp ?: recipe.refractionHeightDp
                val effectiveRefractionAmount = overrides.refractionAmountDp ?: recipe.refractionAmountDp
                val lensEffectsActive = lensControlsAvailable &&
                    effectiveRefractionHeight > 0f &&
                    effectiveRefractionAmount > 0f
                Text(
                    text = stringResource(Res.string.settings_liquid_glass_surface_material_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                )
                materialOptions.forEach { material ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onChanged(overrides.copy(material = material))
                            }
                            .semantics {
                                role = Role.RadioButton
                                selected = material == activeMaterial
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    when (material) {
                                        LiquidGlassMaterial.LIQUID -> Res.string.settings_nav_bar_glass_material_liquid
                                        LiquidGlassMaterial.FROSTED -> Res.string.settings_nav_bar_glass_material_frosted
                                        LiquidGlassMaterial.CLEAR -> Res.string.settings_nav_bar_glass_material_clear
                                        LiquidGlassMaterial.SOLID -> Res.string.settings_nav_bar_glass_material_liquid
                                    },
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (material == LiquidGlassMaterial.CLEAR) {
                                Text(
                                    text = stringResource(Res.string.settings_liquid_glass_suggested),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (material == activeMaterial) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.nuvio.colors.accent)
                        }
                    }
                }
                if (refractionSupported && surface.supportsLens &&
                    materialStyle.usesLens && effectiveCornerRadiusPercent <= 0f
                ) {
                    Text(
                        text = stringResource(Res.string.settings_liquid_glass_lens_requires_rounded_corners),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_highlight),
                    description = stringResource(Res.string.settings_liquid_glass_surface_highlight_description),
                    checked = overrides.withHighlight ?: surface.defaultWithHighlight,
                    isTablet = isTablet,
                    onCheckedChange = { onChanged(overrides.copy(withHighlight = it)) },
                )
                if (surface != LiquidGlassSurface.DETAIL_HEADER) {
                    LiquidGlassEffectSlider(
                        title = stringResource(Res.string.settings_nav_bar_glass_corner_radius),
                        value = overrides.cornerRadiusPercent ?: recipe.cornerRadiusPercent,
                        valueRange = 0f..50f,
                        valueLabel = "${(overrides.cornerRadiusPercent ?: recipe.cornerRadiusPercent).roundToInt()}%",
                        isTablet = isTablet,
                        steps = 49,
                        onValueChange = { onChanged(overrides.copy(cornerRadiusPercent = it)) },
                    )
                }
                if (materialStyle.usesBlur) {
                    LiquidGlassEffectSlider(
                        title = stringResource(Res.string.settings_nav_bar_glass_blur_radius),
                        value = overrides.blurRadiusDp ?: recipe.blurRadiusDp,
                        valueRange = 0f..32f,
                        valueLabel = "${(overrides.blurRadiusDp ?: recipe.blurRadiusDp).roundToInt()} dp",
                        isTablet = isTablet,
                        steps = 31,
                        onValueChange = { onChanged(overrides.copy(blurRadiusDp = it)) },
                    )
                }
                if (lensControlsAvailable) {
                    LiquidGlassEffectSlider(
                        title = stringResource(Res.string.settings_nav_bar_glass_refraction_height),
                        value = overrides.refractionHeightDp ?: recipe.refractionHeightDp,
                        valueRange = 0f..48f,
                        valueLabel = "${(overrides.refractionHeightDp ?: recipe.refractionHeightDp).roundToInt()} dp",
                        isTablet = isTablet,
                        steps = 47,
                        onValueChange = { onChanged(overrides.copy(refractionHeightDp = it)) },
                    )
                    LiquidGlassEffectSlider(
                        title = stringResource(Res.string.settings_nav_bar_glass_refraction_amount),
                        value = overrides.refractionAmountDp ?: recipe.refractionAmountDp,
                        valueRange = 0f..48f,
                        valueLabel = "${(overrides.refractionAmountDp ?: recipe.refractionAmountDp).roundToInt()} dp",
                        isTablet = isTablet,
                        steps = 47,
                        onValueChange = { onChanged(overrides.copy(refractionAmountDp = it)) },
                    )
                }
                LiquidGlassEffectSlider(
                    title = stringResource(Res.string.settings_nav_bar_glass_tint_opacity),
                    value = overrides.tintOpacity ?: recipe.tintOpacity,
                    valueRange = 0f..1f,
                    valueLabel = "${((overrides.tintOpacity ?: recipe.tintOpacity) * 100f).roundToInt()}%",
                    isTablet = isTablet,
                    steps = 19,
                    onValueChange = { onChanged(overrides.copy(tintOpacity = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_vibrancy),
                    description = stringResource(Res.string.settings_liquid_glass_surface_vibrancy_description),
                    checked = overrides.withVibrancy ?: true,
                    isTablet = isTablet,
                    onCheckedChange = { onChanged(overrides.copy(withVibrancy = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_shadow),
                    description = stringResource(Res.string.settings_liquid_glass_surface_shadow_description),
                    checked = overrides.withShadow ?: surface.defaultWithShadow,
                    isTablet = isTablet,
                    onCheckedChange = { onChanged(overrides.copy(withShadow = it)) },
                )
                if (lensEffectsActive) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_nav_bar_glass_chromatic_aberration),
                        description = stringResource(Res.string.settings_nav_bar_glass_chromatic_aberration_description),
                        checked = overrides.chromaticAberration ?: false,
                        isTablet = isTablet,
                        onCheckedChange = { onChanged(overrides.copy(chromaticAberration = it)) },
                    )
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_nav_bar_glass_depth_effect),
                        description = stringResource(Res.string.settings_nav_bar_glass_depth_effect_description),
                        checked = overrides.depthEffect ?: false,
                        isTablet = isTablet,
                        onCheckedChange = { onChanged(overrides.copy(depthEffect = it)) },
                    )
                } else if (lensControlsAvailable) {
                    Text(
                        text = stringResource(Res.string.settings_liquid_glass_lens_requires_active_refraction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (surface.supportsIconTint) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_liquid_glass_custom_icon_color),
                        description = stringResource(Res.string.settings_liquid_glass_custom_icon_color_description),
                        checked = overrides.defaultIconColorArgb != null,
                        isTablet = isTablet,
                        onCheckedChange = {
                            onChanged(
                                overrides.copy(
                                    defaultIconColorArgb = if (it) {
                                        overrides.defaultIconColorArgb ?: 0xFFB7C6E5.toInt()
                                    } else {
                                        null
                                    },
                                ),
                            )
                        },
                    )
                    overrides.defaultIconColorArgb?.let { color ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ThemeColorPicker(
                                color = color,
                                colorIndex = surface.ordinal,
                                onColorChanged = { onChanged(overrides.copy(defaultIconColorArgb = it)) },
                            )
                        }
                    }
                }
            }
        }
        }
        if (!nativeControlsAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onChanged(LiquidGlassSurfaceOverrides()) }) {
                    Text(stringResource(Res.string.settings_liquid_glass_reset))
                }
            }
        }
    }
    NuvioBottomSheetDivider()
}

@Composable
private fun liquidGlassSurfaceTitle(surface: LiquidGlassSurface): String =
    stringResource(
        when (surface) {
            LiquidGlassSurface.PLAYER -> Res.string.settings_liquid_glass_surface_player
            LiquidGlassSurface.STREAM_FILTERS -> Res.string.settings_liquid_glass_surface_stream_filters
            LiquidGlassSurface.STREAM_SELECTION -> Res.string.settings_liquid_glass_surface_stream_selection
            LiquidGlassSurface.STREAM_RESULTS -> Res.string.settings_liquid_glass_surface_stream_results
            LiquidGlassSurface.DETAILS -> Res.string.settings_liquid_glass_surface_details
            LiquidGlassSurface.DETAIL_HEADER -> Res.string.settings_liquid_glass_surface_detail_header
            LiquidGlassSurface.DETAIL_CHIPS -> Res.string.settings_liquid_glass_surface_detail_chips
            LiquidGlassSurface.DETAIL_CONTENT_CARDS -> Res.string.settings_liquid_glass_surface_detail_content_cards
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> Res.string.settings_liquid_glass_surface_detail_primary_action
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR -> Res.string.settings_liquid_glass_surface_detail_trailer_selector
            LiquidGlassSurface.DETAIL_TRAILER_MENU -> Res.string.settings_liquid_glass_surface_detail_trailer_menu
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH -> Res.string.settings_liquid_glass_surface_detail_episode_search
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO -> Res.string.settings_liquid_glass_surface_detail_episode_go_to
            LiquidGlassSurface.DETAIL_EPISODE_SORT -> Res.string.settings_liquid_glass_surface_detail_episode_sort
            LiquidGlassSurface.CATEGORY_HEADER_TITLE -> Res.string.settings_liquid_glass_surface_category_header_title
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> Res.string.settings_liquid_glass_surface_category_header_back_button
            LiquidGlassSurface.SEARCH -> Res.string.settings_liquid_glass_surface_search
            LiquidGlassSurface.LIBRARY -> Res.string.settings_liquid_glass_surface_library
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER -> Res.string.settings_liquid_glass_surface_library_saved_type_filter
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER -> Res.string.settings_liquid_glass_surface_library_saved_sort_filter
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS -> Res.string.settings_liquid_glass_surface_library_cloud_filters
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH -> Res.string.settings_liquid_glass_surface_library_cloud_search
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS -> Res.string.settings_liquid_glass_surface_library_cloud_cards
            LiquidGlassSurface.LIBRARY_CALENDAR -> Res.string.settings_liquid_glass_surface_library_calendar
            LiquidGlassSurface.SETTINGS -> Res.string.settings_liquid_glass_surface_settings
            LiquidGlassSurface.BOTTOM_SHEETS -> Res.string.settings_liquid_glass_surface_bottom_sheets
        },
    )

@Composable
private fun LiquidGlassSurfacePreview(
    surface: LiquidGlassSurface,
    recipe: LiquidGlassSurfaceRecipe,
    overrides: LiquidGlassSurfaceOverrides,
    settings: LiquidGlassSurfacesSettings,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLiquidGlassPreviewSettings provides settings) {
    BoxWithConstraints(
        modifier = modifier
            .then(
                if (surface == LiquidGlassSurface.PLAYER) {
                    Modifier.widthIn(max = LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP.dp)
                } else {
                    Modifier
                },
            )
            .fillMaxWidth(),
    ) {
        val previewWidth = maxWidth
        val previewHeight = when (surface) {
            LiquidGlassSurface.PLAYER -> liquidGlassPlayerPreviewHeight(previewWidth)
            LiquidGlassSurface.SEARCH -> 176.dp
            LiquidGlassSurface.STREAM_FILTERS -> 74.dp
            LiquidGlassSurface.DETAIL_HEADER -> 90.dp
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> 120.dp
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.DETAIL_TRAILER_MENU -> 216.dp
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
            LiquidGlassSurface.DETAIL_EPISODE_SORT -> 188.dp
            LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> 260.dp
            LiquidGlassSurface.DETAIL_CHIPS -> 104.dp
            LiquidGlassSurface.DETAIL_CONTENT_CARDS -> minOf(previewWidth * (184f / 296f), 240.dp)
            LiquidGlassSurface.STREAM_SELECTION -> LIQUID_GLASS_STREAM_SELECTION_PREVIEW_HEIGHT_DP.dp
            LiquidGlassSurface.STREAM_RESULTS -> 212.dp
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            LiquidGlassSurface.LIBRARY_CALENDAR -> 332.dp
            LiquidGlassSurface.SETTINGS -> 148.dp
            LiquidGlassSurface.BOTTOM_SHEETS -> 148.dp
            else -> 88.dp
        }

        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedCornerShape(14.dp))
                .align(Alignment.Center),
        ) {
        when (surface) {
            LiquidGlassSurface.PLAYER -> LiquidGlassPlayerPreview(
                overrides = overrides,
                modifier = Modifier.fillMaxSize(),
            )

            LiquidGlassSurface.SEARCH -> LiquidGlassSearchPreview(
                recipe = recipe,
                modifier = Modifier.fillMaxSize(),
            )

            else -> {
                val previewSheetBackdrop = LocalNuvioSheetBackdrop.current?.backdrop?.takeIf { it.isReady }
                val previewBackdrop = previewSheetBackdrop ?: LocalLiquidGlassBackdrop.current
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides previewBackdrop) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (surface) {
                            LiquidGlassSurface.STREAM_FILTERS -> LiquidGlassStreamFiltersPreview(
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.STREAM_SELECTION -> LiquidGlassStreamSearchPreview(
                                surface = surface,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.STREAM_RESULTS -> LiquidGlassStreamResultsPreview(
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.DETAIL_HEADER -> LiquidGlassDetailHeaderPreview(
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
                            LiquidGlassSurface.DETAIL_TRAILER_MENU,
                            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
                            LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
                            LiquidGlassSurface.DETAIL_EPISODE_SORT -> LiquidGlassDetailControlPreview(
                                surface = surface,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON -> LiquidGlassCatalogHeaderPreview(
                                settings = settings,
                                selectedSurface = surface,
                                onSelectSurface = null,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.DETAILS -> LiquidGlassDetailBackdropPreview(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                DetailActions(
                                    playLabel = stringResource(Res.string.action_play),
                                    playEnabled = true,
                                    isSaved = false,
                                    isWatched = false,
                                    isTablet = isTablet,
                                    shuffleEnabled = false,
                                    onPlayClick = {},
                                    onPlayLongClick = null,
                                    onShuffleClick = null,
                                    onWatchedClick = {},
                                    onSaveClick = {},
                                    onSaveLongClick = null,
                                    onDownloadClick = {},
                                    onPlayFromStartClick = null,
                                    onPlayExternallyClick = {},
                                    iconActionRow = true,
                                    previewGlassSurface = surface,
                                )
                            }

                            LiquidGlassSurface.DETAIL_PRIMARY_ACTION -> LiquidGlassDetailBackdropPreview(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                DetailActions(
                                    playLabel = stringResource(Res.string.action_play),
                                    playEnabled = true,
                                    isSaved = false,
                                    isWatched = false,
                                    isTablet = isTablet,
                                    shuffleEnabled = false,
                                    onPlayClick = {},
                                    onPlayLongClick = null,
                                    onShuffleClick = null,
                                    onWatchedClick = {},
                                    onSaveClick = {},
                                    onSaveLongClick = null,
                                    onDownloadClick = {},
                                    onPlayFromStartClick = null,
                                    onPlayExternallyClick = {},
                                    iconActionRow = false,
                                    previewGlassSurface = surface,
                                )
                            }

                            LiquidGlassSurface.DETAIL_CHIPS -> LiquidGlassDetailBackdropPreview(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                DetailGlassChipsPreview(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                )
                            }

                            LiquidGlassSurface.DETAIL_CONTENT_CARDS -> LiquidGlassDetailBackdropPreview(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                DetailGlassContentCardPreview(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                )
                            }

                            LiquidGlassSurface.LIBRARY,
                            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
                            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
                            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
                            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                            LiquidGlassSurface.LIBRARY_CALENDAR -> LiquidGlassLibraryPreview(
                                settings = settings,
                                selectedSurface = surface,
                                onSelectSurface = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.SETTINGS -> LiquidGlassSettingsSurfacePreview(
                                isTablet = isTablet,
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.BOTTOM_SHEETS -> LiquidGlassBottomSheetPreview(
                                modifier = Modifier.fillMaxSize(),
                            )

                            LiquidGlassSurface.PLAYER,
                            LiquidGlassSurface.SEARCH,
                            -> Unit
                        }
                    }
                }
            }
        }
        }
    }
    }
}


@Composable
private fun LiquidGlassCatalogHeaderPreview(
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    onSelectSurface: ((LiquidGlassSurface) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            // Use the real catalog poster tile/grid so the title glass samples artwork,
            // card sizing and labels from the same content layer as CatalogScreen.
            CatalogHeaderPreviewGrid(modifier = Modifier.fillMaxSize())
        }

        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdrop,
            LocalLiquidGlassPreviewSettings provides settings,
        ) {
            CatalogHeader(
                title = CATEGORY_HEADER_PREVIEW_TITLE,
                subtitle = CATEGORY_HEADER_PREVIEW_SUBTITLE,
                onBack = {},
                modifier = Modifier.fillMaxWidth(),
                previewMode = true,
                onPreviewSurfaceSelected = onSelectSurface,
                selectedPreviewSurface = selectedSurface,
            )
        }
    }
}


@Composable
private fun LiquidGlassDetailControlPreview(
    surface: LiquidGlassSurface,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    val trailers = remember { liquidGlassDetailPreviewTrailers() }
    val episodes = remember { liquidGlassDetailPreviewEpisodes() }
    val navigator = rememberEpisodeNavigatorState("liquid-glass-detail-control-preview")
    val isWatched: (MetaVideo) -> Boolean = { it.episode == 1 }
    val shownEpisodes = remember(episodes, navigator.query, navigator.sort) {
        episodes.filteredAndSortedForNavigator(
            query = navigator.query,
            sort = navigator.sort,
            isWatched = isWatched,
        )
    }
    var jumpTarget by remember { mutableStateOf<MetaVideo?>(null) }
    val episodeToShow = if (navigator.query.isNotBlank()) {
        shownEpisodes.firstOrNull()
    } else {
        jumpTarget ?: shownEpisodes.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A202A),
                                Color(0xFF6A4D58),
                                Color(0xFF302533),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 46.dp, y = (-44).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xD6D7A8B8), Color(0x8A7A596D), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(146.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-42).dp, y = 42.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xC86B9AB3), Color(0x67485E79), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }

        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            if (surface == LiquidGlassSurface.DETAIL_TRAILER_SELECTOR ||
                surface == LiquidGlassSurface.DETAIL_TRAILER_MENU
            ) {
                DetailTrailersSection(
                    trailers = trailers,
                    onTrailerClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    showHeader = true,
                    selectedPreviewSurface = surface,
                    previewMenuInitiallyExpanded = surface == LiquidGlassSurface.DETAIL_TRAILER_MENU,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EpisodeNavigatorBar(
                        state = navigator,
                        shownCount = shownEpisodes.size,
                        totalCount = episodes.size,
                        selectedPreviewSurface = surface,
                        onSubmitNumber = { number ->
                            episodes.firstOrNull { it.episode == number }?.let { episode ->
                                jumpTarget = episode
                                navigator.query = ""
                            }
                        },
                    )
                    if (navigator.goToSheetVisible) {
                        EpisodeGoToSheet(
                            seasonLabel = "Season 1",
                            episodes = episodes,
                            isWatched = isWatched,
                            onPick = { episode -> jumpTarget = episode },
                            onDismiss = { navigator.goToSheetVisible = false },
                        )
                    }
                    if (navigator.sortSheetVisible) {
                        EpisodeSortSheet(
                            current = navigator.sort,
                            onSelect = { navigator.sort = it },
                            onDismiss = { navigator.sortSheetVisible = false },
                        )
                    }
                    if (shownEpisodes.isEmpty()) {
                        EpisodeNavigatorEmptyState(
                            query = navigator.query,
                            onClear = { navigator.query = "" },
                        )
                    } else {
                        DetailGlassContentCardPreview(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(108.dp),
                            video = episodeToShow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassSettingsSurfacePreview(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val customBackgroundImageUri by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.customSettingsBackgroundImageUri
    }.collectAsStateWithLifecycle()
    val backgroundRevision by remember {
        ThemeSettingsRepository.customSettingsBackgroundImageRevision
    }.collectAsStateWithLifecycle()
    val platformContext = LocalPlatformContext.current
    val backgroundImageRequest = remember(customBackgroundImageUri, backgroundRevision, platformContext) {
        customBackgroundImageUri?.takeIf { isLocalBackgroundImageUri(it) }?.let { imageUrl ->
            ImageRequest.Builder(platformContext)
                .data(imageUrl)
                .memoryCacheKey("$imageUrl#settings-background-$backgroundRevision")
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    val backdrop = rememberNuvioBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF101824),
                                Color(0xFF344052),
                                Color(0xFF563B4A),
                                Color(0xFF17141E),
                            ),
                        ),
                    ),
            )
            backgroundImageRequest?.let { imageRequest ->
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f)),
            )

            if (backgroundImageRequest == null) {
                Box(
                    modifier = Modifier
                        .size(206.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 44.dp, y = (-44).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xD8F2B76F), Color(0x807F67E8), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-48).dp, y = 46.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xCF58DACA), Color(0x7B7B65D7), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
            }
        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsGroup(isTablet = isTablet, previewGlass = true) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_appearance),
                        description = stringResource(Res.string.compose_settings_root_appearance_description),
                        icon = Icons.Rounded.Settings,
                        isTablet = isTablet,
                        onClick = {},
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_section_display),
                        description = stringResource(Res.string.settings_liquid_glass_surface_description),
                        icon = Icons.Rounded.Settings,
                        isTablet = isTablet,
                        onClick = {},
                    )
                }
            }
        }
    }
}


private val liquidGlassLibraryCloudBackgroundSurfaces = setOf(
    LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
    LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
    LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
)

private data class LiquidGlassCloudPreviewItem(
    val matchText: String,
    val providerId: String,
    val type: CloudLibraryItemType,
)

@Composable
private fun LiquidGlassLibraryPosterBackdrop(
    state: androidx.compose.foundation.lazy.LazyListState,
    baseBackground: Color,
) {
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .background(baseBackground),
        contentPadding = PaddingValues(top = 28.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = liquidGlassSearchPreviewPosterRows,
            key = { row -> row.first().title },
        ) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { poster ->
                    LiquidGlassSearchPreviewPosterTile(
                        poster = poster,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassLibraryCloudBackdrop() {
    val platformContext = LocalPlatformContext.current
    val cloudBackgroundImageUri by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.customLibraryCloudBackgroundImageUri
    }.collectAsStateWithLifecycle()
    val cloudBackgroundImageRevision by remember {
        ThemeSettingsRepository.customLibraryCloudBackgroundImageRevision
    }.collectAsStateWithLifecycle()
    val cloudBackgroundImageRequest = remember(
        cloudBackgroundImageUri,
        cloudBackgroundImageRevision,
        platformContext,
    ) {
        cloudBackgroundImageUri?.takeIf { isLocalBackgroundImageUri(it) }?.let { imageUri ->
            ImageRequest.Builder(platformContext)
                .data(imageUri)
                // Keep preview-sized decodes separate from the full-screen Library image cache entry.
                .memoryCacheKey("$imageUri#library-cloud-preview-$cloudBackgroundImageRevision")
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }

    // Cloud has its own backdrop: black by default, with only the selected custom image above it.
    // Keeping black underneath also prevents stale/failed image loads from revealing Saved artwork.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        cloudBackgroundImageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun LiquidGlassLibraryPreview(
    settings: LiquidGlassSurfacesSettings,
    selectedSurface: LiquidGlassSurface,
    onSelectSurface: (LiquidGlassSurface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    val posterBackdropListState = rememberLazyListState()
    var viewModeName by rememberSaveable { mutableStateOf(LibraryViewMode.Saved.name) }
    val viewMode = remember(viewModeName) {
        runCatching { LibraryViewMode.valueOf(viewModeName) }.getOrDefault(LibraryViewMode.Saved)
    }
    var savedType by rememberSaveable { mutableStateOf<String?>(null) }
    var savedSort by rememberSaveable { mutableStateOf(LibrarySortOption.ADDED_DESC.name) }
    var cloudProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var cloudTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var cloudQuery by rememberSaveable { mutableStateOf("") }
    var showCalendarPreview by rememberSaveable(selectedSurface) {
        mutableStateOf(selectedSurface == LiquidGlassSurface.LIBRARY_CALENDAR)
    }

    val cloudPreviewItems = remember {
        listOf(
            LiquidGlassCloudPreviewItem("Tenzing 2160p HDR release", "preview:torrentio", CloudLibraryItemType.Torrent),
            LiquidGlassCloudPreviewItem("Nobody Wants This season 2", "preview:comet", CloudLibraryItemType.Usenet),
            LiquidGlassCloudPreviewItem("Foundation episode 5", "preview:torrentio", CloudLibraryItemType.WebDownload),
            LiquidGlassCloudPreviewItem("One Piece latest episode", "preview:comet", CloudLibraryItemType.File),
            LiquidGlassCloudPreviewItem("Cloud download complete", "preview:torrentio", CloudLibraryItemType.Torrent),
        )
    }
    val visibleCloudPreviewItems = remember(cloudPreviewItems, cloudQuery, cloudProvider, cloudTypeName) {
        cloudPreviewItems.filter { item ->
            (cloudQuery.isBlank() || item.matchText.contains(cloudQuery, ignoreCase = true)) &&
                (cloudProvider.isNullOrBlank() || item.providerId == cloudProvider) &&
                (cloudTypeName.isNullOrBlank() || item.type.name == cloudTypeName)
        }
    }
    val libraryRecipe = remember(settings) {
        liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY, settings.forSurface(LiquidGlassSurface.LIBRARY))
    }
    val cloudSearchRecipe = remember(settings) {
        liquidGlassSurfaceRecipe(
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
            settings.forSurface(LiquidGlassSurface.LIBRARY_CLOUD_SEARCH),
        )
    }
    val cloudCardsRecipe = remember(settings) {
        liquidGlassSurfaceRecipe(
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            settings.forSurface(LiquidGlassSurface.LIBRARY_CLOUD_CARDS),
        )
    }
    val savedSortOption = remember(savedSort) {
        runCatching { LibrarySortOption.valueOf(savedSort) }
            .getOrDefault(LibrarySortOption.ADDED_DESC)
    }
    val rendererAvailable = isLiquidGlassAvailable()
    val baseBackground = MaterialTheme.colorScheme.background
    val headerHeight = if (viewMode == LibraryViewMode.Cloud) 132.dp else 190.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(332.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        // Saved uses poster artwork; Cloud uses black unless its custom image is available.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            if (viewMode == LibraryViewMode.Cloud) {
                LiquidGlassLibraryCloudBackdrop()
            } else {
                LiquidGlassLibraryPosterBackdrop(
                    state = posterBackdropListState,
                    baseBackground = baseBackground,
                )
            }
        }

        // Keep Cloud filters, search and the blank icon cards in one scroll container, matching production.
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides backdrop,
            LocalLiquidGlassPreviewSettings provides settings,
        ) {
            if (viewMode == LibraryViewMode.Cloud) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = headerHeight, bottom = 8.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "library-cloud-preview-controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val providerOptions = remember {
                                listOf(
                                    NuvioDropdownOption("", "All providers"),
                                    NuvioDropdownOption("preview:torrentio", "Torrentio"),
                                    NuvioDropdownOption("preview:comet", "Comet"),
                                )
                            }
                            CloudLibraryToolbar(
                                uiState = CloudLibraryUiState(isLoaded = true, isEnabled = true),
                                selectedProviderId = cloudProvider,
                                selectedType = cloudTypeName
                                    ?.let { name -> CloudLibraryItemType.entries.firstOrNull { it.name == name } },
                                availableTypes = CloudLibraryItemType.entries,
                                onProviderSelected = { cloudProvider = it },
                                onTypeSelected = { cloudTypeName = it?.name },
                                onRefresh = {},
                                modifier = Modifier.padding(horizontal = 8.dp),
                                previewGlass = true,
                                selectedPreviewSurface = selectedSurface,
                                onPreviewSurfaceSelected = onSelectSurface,
                                previewProviderOptions = providerOptions,
                            )

                            val cloudSearchShape = liquidGlassShape(
                                surface = LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                                defaultShape = RoundedCornerShape(12.dp),
                                preview = true,
                            )
                            val cloudSearchGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
                                surface = LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                                preview = true,
                                platformSupported = rendererAvailable,
                            )
                            CloudLibrarySearchField(
                                query = cloudQuery,
                                onQueryChange = { cloudQuery = it },
                                nativeGlassEnabled = isNativeLiquidGlassControlsAvailable() &&
                                    (selectedSurface == LiquidGlassSurface.LIBRARY_CLOUD_SEARCH) &&
                                    isLiquidGlassSurfaceEffectivelyEnabled(
                                        LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                                        preview = true,
                                        platformSupported = true,
                                    ),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .then(
                                        if (cloudSearchGlassEnabled) {
                                            Modifier.liquidGlassForSurface(
                                                surface = LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                                                shape = cloudSearchShape,
                                                tint = MaterialTheme.colorScheme.surface.copy(alpha = cloudSearchRecipe.tintOpacity),
                                                fallback = MaterialTheme.nuvio.colors.surfaceCard,
                                                blurRadius = cloudSearchRecipe.blurRadiusDp.dp,
                                                refractionHeight = cloudSearchRecipe.refractionHeightDp.dp,
                                                refractionAmount = cloudSearchRecipe.refractionAmountDp.dp,
                                                preview = true,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                transparentContainer = cloudSearchGlassEnabled,
                                shape = cloudSearchShape,
                                showOutline = shouldDrawNuvioInputFieldOutline(
                                    glassSurfaceEnabled = cloudSearchGlassEnabled,
                                    glassRimCanRender = cloudSearchGlassEnabled && isLiquidGlassBackdropReady(backdrop),
                                ),
                                onGlassSurfaceInteracted = {
                                    onSelectSurface(LiquidGlassSurface.LIBRARY_CLOUD_SEARCH)
                                },
                            )
                        }
                    }
                    items(
                        items = visibleCloudPreviewItems,
                        key = { item -> item.matchText },
                    ) { item ->
                        val cardShape = liquidGlassShape(
                            surface = LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                            defaultShape = MaterialTheme.shapes.medium,
                            preview = true,
                        )
                        val cardGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
                            surface = LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                            preview = true,
                            platformSupported = rendererAvailable,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .then(
                                    if (cardGlassEnabled) {
                                        Modifier.liquidGlassForSurface(
                                            surface = LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                                            shape = cardShape,
                                            tint = MaterialTheme.colorScheme.surface.copy(alpha = cloudCardsRecipe.tintOpacity),
                                            fallback = MaterialTheme.nuvio.colors.surfaceCard,
                                            blurRadius = cloudCardsRecipe.blurRadiusDp.dp,
                                            refractionHeight = cloudCardsRecipe.refractionHeightDp.dp,
                                            refractionAmount = cloudCardsRecipe.refractionAmountDp.dp,
                                            withShadow = true,
                                            preview = true,
                                        )
                                    } else {
                                        Modifier
                                            .clip(cardShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow, cardShape)
                                    },
                                )
                                .then(
                                    if (selectedSurface == LiquidGlassSurface.LIBRARY_CLOUD_CARDS) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSelectSurface(LiquidGlassSurface.LIBRARY_CLOUD_CARDS) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = stringResource(
                                        Res.string.settings_liquid_glass_surface_library_cloud_cards,
                                    ),
                                    modifier = Modifier.padding(top = 2.dp).size(22.dp),
                                    tint = liquidGlassIconTint(
                                        LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                                        if (cardGlassEnabled) {
                                            MaterialTheme.nuvio.colors.textSecondary
                                        } else {
                                            MaterialTheme.nuvio.colors.textMuted
                                        },
                                    ),
                                )
                                LiquidGlassTextContrastScope(enabled = cardGlassEnabled) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = item.matchText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cardGlassEnabled) {
                                            MaterialTheme.nuvio.colors.textPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.providerId.substringAfter(':')} • ${item.type.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (cardGlassEnabled) {
                                            MaterialTheme.nuvio.colors.textSecondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${stringResource(Res.string.cloud_library_status_ready)} • ${stringResource(Res.string.cloud_library_playable_file_count, 3)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (cardGlassEnabled) {
                                            MaterialTheme.nuvio.colors.textMuted
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                    )
                                }
                                }
                                if (item.type != CloudLibraryItemType.File) {
                                    IconButton(
                                        onClick = { onSelectSurface(LiquidGlassSurface.LIBRARY_CLOUD_CARDS) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(Res.string.action_play),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The poster-backed surface scrolls behind a separate fixed Library header, like Search.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight + 8.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                baseBackground.copy(alpha = 0.28f),
                                baseBackground.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.library_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )

                val sourceSwitchShape = liquidGlassShape(
                    surface = LiquidGlassSurface.LIBRARY,
                    defaultShape = RoundedCornerShape(20.dp),
                    preview = true,
                )
                val sourceSwitchGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
                    surface = LiquidGlassSurface.LIBRARY,
                    preview = true,
                    platformSupported = rendererAvailable,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    LibrarySourceSwitch(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (sourceSwitchGlassEnabled) {
                                    Modifier.liquidGlassForSurface(
                                        surface = LiquidGlassSurface.LIBRARY,
                                        shape = sourceSwitchShape,
                                        tint = MaterialTheme.colorScheme.surface.copy(alpha = libraryRecipe.tintOpacity),
                                        fallback = MaterialTheme.nuvio.colors.surfaceCard,
                                        blurRadius = libraryRecipe.blurRadiusDp.dp,
                                        refractionHeight = libraryRecipe.refractionHeightDp.dp,
                                        refractionAmount = libraryRecipe.refractionAmountDp.dp,
                                        preview = true,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (selectedSurface == LiquidGlassSurface.LIBRARY) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, sourceSwitchShape)
                                } else {
                                    Modifier
                                },
                            ),
                        selectedMode = viewMode,
                        showServers = false,
                        onModeSelected = { viewModeName = it.name },
                        onPreviewModeSelected = { mode ->
                            viewModeName = mode.name
                            onSelectSurface(
                                if (mode == LibraryViewMode.Cloud) {
                                    LiquidGlassSurface.LIBRARY_CLOUD_FILTERS
                                } else {
                                    LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER
                                },
                            )
                        },
                        trailingActions = {
                            if (viewMode == LibraryViewMode.Saved) {
                                IconButton(onClick = { onSelectSurface(LiquidGlassSurface.LIBRARY) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = liquidGlassIconTint(
                                            LiquidGlassSurface.LIBRARY,
                                            MaterialTheme.nuvio.colors.textMuted,
                                        ),
                                    )
                                }
                                IconButton(onClick = { onSelectSurface(LiquidGlassSurface.LIBRARY) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        tint = liquidGlassIconTint(
                                            LiquidGlassSurface.LIBRARY,
                                            MaterialTheme.nuvio.colors.textMuted,
                                        ),
                                    )
                                }
                                IconButton(onClick = { showCalendarPreview = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = liquidGlassIconTint(
                                            LiquidGlassSurface.LIBRARY,
                                            MaterialTheme.nuvio.colors.textMuted,
                                        ),
                                    )
                                }
                            }
                            IconButton(onClick = { onSelectSurface(LiquidGlassSurface.LIBRARY) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = liquidGlassIconTint(
                                        LiquidGlassSurface.LIBRARY,
                                        MaterialTheme.nuvio.colors.textMuted,
                                    ),
                                )
                            }
                        },
                    )
                }

                if (viewMode == LibraryViewMode.Saved) {
                    val projection = remember(savedType) {
                        LibraryVerticalProjection(
                            availableSections = emptyList(),
                            selectedSectionKey = null,
                            availableTypes = listOf("movie", "series"),
                            selectedType = savedType,
                            entries = emptyList(),
                        )
                    }
                    val savedTypeGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
                        surface = LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
                        preview = true,
                        platformSupported = rendererAvailable,
                    )
                    val savedSortGlassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
                        surface = LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
                        preview = true,
                        platformSupported = rendererAvailable,
                    )
                    LiquidGlassTextContrastScope(
                        enabled = savedTypeGlassEnabled || savedSortGlassEnabled,
                    ) {
                    LibrarySavedControls(
                        layoutMode = LibraryLayoutMode.VERTICAL,
                        sourceMode = LibrarySourceMode.LOCAL,
                        sortOption = savedSortOption,
                        verticalProjection = projection,
                        onSectionSelected = {},
                        onTypeSelected = { savedType = it },
                        onSortSelected = { savedSort = it.name },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(top = 8.dp, bottom = 14.dp),
                        typeGlassSurface = LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
                        sortGlassSurface = LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
                        previewGlass = true,
                        selectedPreviewSurface = selectedSurface,
                        onPreviewSurfaceSelected = onSelectSurface,
                    )
                    }
                }
            }
            if (showCalendarPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.44f))
                        .clickable { showCalendarPreview = false },
                    contentAlignment = Alignment.Center,
                ) {
                    LibraryCalendarGlassPreview(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(300.dp),
                        selectedPreview = selectedSurface == LiquidGlassSurface.LIBRARY_CALENDAR,
                        onSelect = { onSelectSurface(LiquidGlassSurface.LIBRARY_CALENDAR) },
                        onDismissPreview = { showCalendarPreview = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassBottomSheetPreview(
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF102638), Color(0xFF435064), Color(0xFF683A52), Color(0xFF17141C)),
                    ),
                ),
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 22.dp, y = (-42).dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xD763BCD5), Color(0x7E9560AB), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-24).dp, y = 24.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xC9D56B9D), Color.Transparent)),
                        CircleShape,
                    ),
            )

        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .nuvioSheetSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.nuvio.colors.surfaceSheet,
                    preview = true,
                ),
        ) {
            LiquidGlassTextContrastScope(
                enabled = isLiquidGlassSurfaceEffectivelyEnabled(
                    surface = LiquidGlassSurface.BOTTOM_SHEETS,
                    preview = true,
                    platformSupported = isLiquidGlassAvailable(),
                ),
            ) {
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.compose_settings_page_appearance),
                icon = Icons.Rounded.Settings,
                onClick = {},
            )
            NuvioBottomSheetDivider()
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.settings_appearance_section_display),
                icon = Icons.Rounded.Settings,
                onClick = {},
            )
            }
        }
    }
}

@Composable
private fun LiquidGlassStreamFiltersPreview(modifier: Modifier = Modifier) {
    val groups = remember { liquidGlassPreviewStreamGroups() }
    val backdrop = rememberNuvioBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF12283B), Color(0xFF3A394B), Color(0xFF40263D), Color(0xFF17141F)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 22.dp, y = (-32).dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xD85AB8D0), Color(0x85774F9D), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-18).dp, y = 28.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xD5C76A9E), Color.Transparent)),
                        CircleShape,
                    ),
            )

        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ProviderFilterRow(
                    groups = groups,
                    selectedFilter = "preview:torrentio",
                    onFilterSelected = {},
                    onRefresh = {},
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    spacing = 6.dp,
                    preview = true,
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassPlayerPreview(
    overrides: LiquidGlassSurfaceOverrides,
    modifier: Modifier = Modifier,
) {
    val previewBackdrop = rememberNuvioBackdrop()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val previewScale = liquidGlassPlayerPreviewScale(maxWidth)
        val metrics = remember {
            PlayerLayoutMetrics.fromWidth(LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP.dp)
        }
        val durationMs = 10_871_000L
        val playbackSnapshot = remember {
            PlayerPlaybackSnapshot(
                isLoading = false,
                isPlaying = false,
                durationMs = durationMs,
                positionMs = 0L,
                bufferedPositionMs = 3_760_000L,
                playbackSpeed = 1f,
                videoWidth = 3840,
                videoHeight = 2160,
                chapters = listOf(
                    PlayerChapter(0L, "Opening"),
                    PlayerChapter(1_500_000L, "Arrival"),
                    PlayerChapter(3_400_000L, "Midpoint"),
                    PlayerChapter(7_100_000L, "Finale"),
                ),
            )
        }

        // One landscape design canvas contains both the artwork source and the real Nuvio player
        // controls. They are measured at the same 800x450 size and uniformly scaled as a unit.
        Box(
            modifier = Modifier
                .requiredSize(
                    width = LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP.dp,
                    height = LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP.dp * 9f / 16f,
                )
                .graphicsLayer {
                    scaleX = previewScale
                    scaleY = previewScale
                    transformOrigin = TransformOrigin.Center
                }
                .align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nuvioBackdropSource(previewBackdrop),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0C1827),
                                    Color(0xFF273A52),
                                    Color(0xFF493447),
                                    Color(0xFF101018),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(390.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = 105.dp, y = (-24).dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xD03F9CB7), Color(0x8A3E607A), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(330.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 82.dp, y = 68.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xC77C4C75), Color(0x6A583E64), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                // Use a stylized cinematic landscape instead of decorative diagonal strokes.
                // The layered silhouettes and scattered lights provide real edges for blur/refraction
                // to sample while keeping the preview atmospheric rather than diagram-like.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val starPoints = listOf(
                        Offset(w * 0.08f, h * 0.20f),
                        Offset(w * 0.19f, h * 0.13f),
                        Offset(w * 0.31f, h * 0.28f),
                        Offset(w * 0.43f, h * 0.15f),
                        Offset(w * 0.57f, h * 0.24f),
                        Offset(w * 0.73f, h * 0.12f),
                        Offset(w * 0.88f, h * 0.22f),
                        Offset(w * 0.95f, h * 0.37f),
                    )
                    starPoints.forEachIndexed { index, point ->
                        val color = when (index % 3) {
                            0 -> Color(0xFFC8F0FF)
                            1 -> Color(0xFFFFD9BA)
                            else -> Color(0xFFD7C9FF)
                        }
                        drawCircle(
                            color = color.copy(alpha = if (index % 3 == 0) 0.58f else 0.34f),
                            radius = (if (index % 3 == 0) 1.7f else 1.1f).dp.toPx(),
                            center = point,
                        )
                    }

                    val distantRidge = Path().apply {
                        moveTo(0f, h * 0.64f)
                        cubicTo(w * 0.12f, h * 0.57f, w * 0.19f, h * 0.43f, w * 0.31f, h * 0.54f)
                        cubicTo(w * 0.43f, h * 0.65f, w * 0.48f, h * 0.47f, w * 0.60f, h * 0.51f)
                        cubicTo(w * 0.73f, h * 0.57f, w * 0.83f, h * 0.42f, w, h * 0.56f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = distantRidge,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF52647A), Color(0xFF273246)),
                            startY = h * 0.42f,
                            endY = h,
                        ),
                    )

                    val middleRidge = Path().apply {
                        moveTo(0f, h * 0.77f)
                        cubicTo(w * 0.13f, h * 0.68f, w * 0.23f, h * 0.64f, w * 0.36f, h * 0.76f)
                        cubicTo(w * 0.49f, h * 0.88f, w * 0.57f, h * 0.67f, w * 0.70f, h * 0.72f)
                        cubicTo(w * 0.82f, h * 0.76f, w * 0.90f, h * 0.67f, w, h * 0.74f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = middleRidge,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF263245), Color(0xFF131A28)),
                            startY = h * 0.63f,
                            endY = h,
                        ),
                    )

                    val foregroundRidge = Path().apply {
                        moveTo(0f, h * 0.91f)
                        cubicTo(w * 0.18f, h * 0.82f, w * 0.29f, h * 0.96f, w * 0.46f, h * 0.87f)
                        cubicTo(w * 0.63f, h * 0.78f, w * 0.76f, h * 0.94f, w, h * 0.83f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = foregroundRidge,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF17202C), Color(0xFF090D16)),
                            startY = h * 0.77f,
                            endY = h,
                        ),
                    )

                    val horizonLights = listOf(
                        Offset(w * 0.12f, h * 0.78f),
                        Offset(w * 0.20f, h * 0.82f),
                        Offset(w * 0.28f, h * 0.76f),
                        Offset(w * 0.39f, h * 0.83f),
                        Offset(w * 0.66f, h * 0.79f),
                        Offset(w * 0.78f, h * 0.81f),
                        Offset(w * 0.89f, h * 0.76f),
                    )
                    horizonLights.forEachIndexed { index, point ->
                        drawCircle(
                            color = if (index % 2 == 0) {
                                Color(0xFFFFC98B).copy(alpha = 0.72f)
                            } else {
                                Color(0xFF91DDEB).copy(alpha = 0.60f)
                            },
                            radius = (if (index % 3 == 0) 2.2f else 1.3f).dp.toPx(),
                            center = point,
                        )
                    }
                }
            }

            CompositionLocalProvider(
                LocalLiquidGlassBackdrop provides previewBackdrop,
                LocalLiquidGlassPreviewOverrides provides overrides,
            ) {
                PlayerControlsShell(
                    title = "Avengers: Endgame",
                    streamTitle = "One Last Surprise",
                    providerName = "Torrentio",
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeTitle = null,
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = 0L,
                    metrics = metrics,
                    resizeMode = PlayerResizeMode.Fit,
                    isLocked = false,
                    releaseInfo = "Via 2160p | Cached",
                    badges = emptyList(),
                    onLockToggle = {},
                    onBack = {},
                    onPartyClick = {},
                    partyActive = false,
                    onCastClick = {},
                    isCasting = false,
                    onPictureInPictureClick = {},
                    onOrientationToggleClick = {},
                    isPortraitPlayback = false,
                    onTogglePlayback = {},
                    onSeekBack = {},
                    onSeekForward = {},
                    skipBackwardSeconds = 10,
                    skipForwardSeconds = 10,
                    onResizeModeClick = {},
                    onSpeedClick = {},
                    onSubtitleClick = {},
                    onAudioClick = {},
                    onVideoSettingsClick = {},
                    onInfoClick = {},
                    onRateClick = {},
                    onSourcesClick = {},
                    onEpisodesClick = {},
                    onChaptersClick = {},
                    onLiveChannelsClick = {},
                    qualityLabel = "2160p",
                    onQualityClick = {},
                    onOpenInExternalPlayer = {},
                    onSubmitIntroClick = {},
                    onSwitchEngineClick = {},
                    onScrubChange = {},
                    onScrubFinished = {},
                    horizontalSafePadding = metrics.horizontalPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassSearchPreview(
    recipe: LiquidGlassSurfaceRecipe,
    modifier: Modifier = Modifier,
) {
    val previewBackdrop = rememberNuvioBackdrop()
    val listState = rememberLazyListState()
    val shape = liquidGlassShape(
        surface = LiquidGlassSurface.SEARCH,
        defaultShape = RoundedCornerShape(18.dp),
        preview = true,
    )
    val glassSurfaceEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
        surface = LiquidGlassSurface.SEARCH,
        preview = true,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(previewBackdrop),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(top = 42.dp, bottom = 12.dp, start = 10.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = liquidGlassSearchPreviewPosterRows,
                    key = { row -> row.first().title },
                ) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { poster ->
                            LiquidGlassSearchPreviewPosterTile(
                                poster = poster,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // Soft scroll-edge fade keeps the floating search field legible while poster art moves beneath it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        CompositionLocalProvider(LocalLiquidGlassBackdrop provides previewBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .liquidGlassForSurface(
                        surface = LiquidGlassSurface.SEARCH,
                        shape = shape,
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = recipe.tintOpacity),
                        fallback = MaterialTheme.nuvio.colors.surfaceCard,
                        blurRadius = recipe.blurRadiusDp.dp,
                        refractionHeight = recipe.refractionHeightDp.dp,
                        refractionAmount = recipe.refractionAmountDp.dp,
                        preview = true,
                    )
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp),
            ) {
                LiquidGlassTextContrastScope(enabled = glassSurfaceEnabled) {
                NuvioInputField(
                    value = "",
                    onValueChange = {},
                    placeholder = stringResource(Res.string.compose_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    shape = shape,
                    showOutline = shouldDrawNuvioInputFieldOutline(
                        glassSurfaceEnabled = glassSurfaceEnabled,
                        glassRimCanRender = glassSurfaceEnabled && isLiquidGlassBackdropReady(previewBackdrop),
                    ),
                )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassDetailBackdropPreview(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberNuvioBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF29212A),
                                Color(0xFF57414B),
                                Color(0xFF29212D),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 55.dp, y = (-45).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xD4D2A4B5), Color(0x7F8A6378), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-48).dp, y = 72.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xAC4F88A8), Color(0x5B3A5D73), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )

        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LiquidGlassDetailHeaderPreview(
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    val previewMeta = remember {
        MetaDetails(
            id = "liquid-glass-preview",
            type = "series",
            name = "Nobody Wants This",
            releaseInfo = "2024",
            status = "Ongoing",
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A202A),
                                Color(0xFF56404A),
                                Color(0xFF251C27),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 42.dp, y = (-48).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xCCD0A1B0), Color(0x6A7A5A6C), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )

        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            DetailFloatingHeader(
                meta = previewMeta,
                isSaved = false,
                // Render the real header in its visible state so its fallback title is inspectable.
                progress = 1f,
                onBack = {},
                onToggleSaved = {},
                modifier = Modifier.fillMaxWidth(),
                glassBackdrop = backdrop,
                previewGlass = true,
            )
        }
    }
}

@Composable
private fun LiquidGlassStreamSearchPreview(
    surface: LiquidGlassSurface,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1D2735),
                                Color(0xFF384453),
                                Color(0xFF1A1C25),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xC8F2B86C), Color(0x776A5CB6), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(154.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-30).dp, y = 34.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xC358D9D0), Color(0x706A7EE8), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreamSearchField(
                    query = "",
                    onQueryChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    preview = true,
                    nativeGlassEnabled = surface == LiquidGlassSurface.STREAM_SELECTION &&
                        isNativeLiquidGlassControlsAvailable() &&
                        isLiquidGlassSurfaceEffectivelyEnabled(
                            LiquidGlassSurface.STREAM_SELECTION,
                            preview = true,
                            platformSupported = true,
                        ),
                )
                ResumeBanner(
                    positionMs = null,
                    progressFraction = 0.42f,
                    modifier = Modifier.fillMaxWidth(),
                    preview = true,
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassStreamResultsPreview(
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberNuvioBackdrop()
    val samples = remember {
        listOf(
            StreamItem(
                name = "2160p | Cached | Torrentio",
                title = "Nobody Wants This S01E01 Pilot",
                description = "WEB-DL • H.265 • DD+ 5.1",
                addonName = "Torrentio",
                addonId = "preview:torrentio",
                streamType = "series",
                badges = listOf(
                    StreamBadge(name = "2160p"),
                    StreamBadge(name = "WEB-DL"),
                    StreamBadge(name = "HEVC"),
                ),
                behaviorHints = com.nuvio.app.features.streams.StreamBehaviorHints(videoSize = 1_100_000_000L),
            ),
            StreamItem(
                name = "1080p | Cached | Torrentio",
                title = "Nobody Wants This S01E01 Pilot",
                description = "WEB-DL • AVC • AAC 2.0",
                addonName = "Torrentio",
                addonId = "preview:torrentio",
                streamType = "series",
                badges = listOf(
                    StreamBadge(name = "1080p"),
                    StreamBadge(name = "WEB-DL"),
                    StreamBadge(name = "AVC"),
                ),
                behaviorHints = com.nuvio.app.features.streams.StreamBehaviorHints(videoSize = 473_000_000L),
            ),
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nuvioBackdropSource(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF25212E),
                                Color(0xFF384552),
                                Color(0xFF241B27),
                                Color(0xFF15141A),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 58.dp, y = (-58).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xD07D9AAF), Color(0x7262768A), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(188.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-42).dp, y = 52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xC7DC79A7), Color(0x736A7EE8), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                samples.forEach { stream ->
                    StreamCard(
                        stream = stream,
                        enabled = true,
                        appendInstantServiceToDefaultName = false,
                        showFileSizeBadges = true,
                        showAddonLogo = false,
                        badgePlacement = StreamBadgePlacement.BOTTOM,
                        onClick = {},
                        preview = true,
                    )
                }
            }
        }
    }
}

private data class LiquidGlassSearchPreviewPoster(
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val glow: Color,
)

private val liquidGlassSearchPreviewPosters = listOf(
    LiquidGlassSearchPreviewPoster("AVENGERS", "ENDGAME", listOf(Color(0xFF173550), Color(0xFF322D3B)), Color(0xFF6BAFC8)),
    LiquidGlassSearchPreviewPoster("DUNE", "PART TWO", listOf(Color(0xFF6F4B31), Color(0xFF1E2838)), Color(0xFFE4B36B)),
    LiquidGlassSearchPreviewPoster("THE BATMAN", "GOTHAM", listOf(Color(0xFF421D28), Color(0xFF10151D)), Color(0xFFB84F58)),
    LiquidGlassSearchPreviewPoster("ARCANE", "LEAGUE OF LEGENDS", listOf(Color(0xFF31546B), Color(0xFF221B3D)), Color(0xFF69D8DA)),
    LiquidGlassSearchPreviewPoster("INTERSTELLAR", "BEYOND STARS", listOf(Color(0xFF263B55), Color(0xFF0F121B)), Color(0xFF94C5E7)),
    LiquidGlassSearchPreviewPoster("SPIDER-VERSE", "ACROSS THE MULTIVERSE", listOf(Color(0xFF592A5A), Color(0xFF172A4A)), Color(0xFFCC5CC7)),
    LiquidGlassSearchPreviewPoster("THE MATRIX", "RELOADED", listOf(Color(0xFF183D31), Color(0xFF090D0C)), Color(0xFF5EC69D)),
    LiquidGlassSearchPreviewPoster("LOKI", "SEASON TWO", listOf(Color(0xFF4B5B37), Color(0xFF20201C)), Color(0xFFB4D472)),
    LiquidGlassSearchPreviewPoster("ARRIVAL", "THE UNKNOWN", listOf(Color(0xFF4C6174), Color(0xFF171A26)), Color(0xFF9DCAF1)),
)

private val liquidGlassSearchPreviewPosterRows = liquidGlassSearchPreviewPosters.chunked(3)

@Composable
private fun LiquidGlassSearchPreviewPosterTile(
    poster: LiquidGlassSearchPreviewPoster,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(poster.colors)),
        )
        Box(
            modifier = Modifier
                .size(92.dp)
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-15).dp)
                .background(
                    Brush.radialGradient(listOf(poster.glow.copy(alpha = 0.82f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = poster.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = poster.subtitle,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 7.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun resolvedGlassEffects(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
): LiquidGlassSurfaceOverrides {
    val recipe = liquidGlassSurfaceRecipe(surface, overrides)
    return overrides.copy(
        enabled = overrides.enabled ?: surface.defaultEnabled,
        blurRadiusDp = overrides.blurRadiusDp ?: recipe.blurRadiusDp,
        refractionHeightDp = overrides.refractionHeightDp ?: recipe.refractionHeightDp,
        refractionAmountDp = overrides.refractionAmountDp ?: recipe.refractionAmountDp,
        tintOpacity = overrides.tintOpacity ?: recipe.tintOpacity,
        withVibrancy = overrides.withVibrancy ?: true,
        withHighlight = overrides.withHighlight ?: surface.defaultWithHighlight,
        withShadow = overrides.withShadow ?: surface.defaultWithShadow,
        chromaticAberration = overrides.chromaticAberration ?: false,
        depthEffect = overrides.depthEffect ?: false,
        material = effectiveLiquidGlassMaterial(
            surface = surface,
            material = overrides.material,
            refractionSupported = isLiquidRefractionSupported(),
            cornerRadiusPercent = recipe.cornerRadiusPercent,
        ),
    ).sanitized()
}

