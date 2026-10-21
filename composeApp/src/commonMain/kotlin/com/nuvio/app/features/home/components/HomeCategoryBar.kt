package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.features.home.HOME_CATEGORY_HOME_ID
import com.nuvio.app.core.ui.NuvioTokens
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.SolidGlassBar
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.LiquidGlassFrostedBar
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.resolveNavigationGlass
import com.nuvio.app.core.ui.forNavigationMaterial
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.draw.clip
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.HOME_CATEGORY_ANIME_ID
import com.nuvio.app.features.home.HOME_CATEGORY_MOVIES_ID
import com.nuvio.app.features.home.HOME_CATEGORY_TV_ID
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCategory
import com.nuvio.app.features.home.HomeCategoryKind
import com.nuvio.app.features.home.HomeCategoryRepository
import com.nuvio.app.features.home.HomeRowShapeMode
import com.nuvio.app.features.home.nextRowShapeMode
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState

/**
 * Panel that opens just above the bottom navigation bar. It reuses the navigation bar's glass
 * look: with a floating/liquid-glass bar it draws the same frosted/refracting surface from the
 * same backdrop and settings; with the classic bar it falls back to a plain rounded surface.
 */
@Composable
internal fun HomeCategoryPanel(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    glassStyle: LiquidGlassStyle = LiquidGlassStyle.Default,
    glassEffects: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.floatingBar,
    showLabels: Boolean = true,
) {
    LaunchedEffect(Unit) { HomeCategoryRepository.ensureLoaded() }
    val state by HomeCategoryRepository.state.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio

    val glassMode = resolveNavigationGlass(
        style = glassStyle,
        hasBackdrop = backdrop != null,
        hasSelection = false,
        glassSupported = isLiquidGlassSupported(),
        refractionSupported = isLiquidRefractionSupported(),
    )
    val barEffects = glassEffects.sanitized(
        LiquidGlassEffectDefaults.floatingBar,
        LiquidGlassEffectDefaults.floatingBarLimits,
    ).forNavigationMaterial(glassStyle, glassMode.barRefraction)

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            // Same compact width as in portrait, even on a wide landscape screen.
            .widthIn(max = 400.dp)
            .animateContentSize(),
    ) {
        when {
            glassMode.bar && backdrop != null -> LiquidGlassFrostedBar(
                backdrop = backdrop,
                modifier = Modifier.matchParentSize(),
                glowStrength = 1f,
                settings = barEffects,
                style = glassStyle,
            )

            backdrop != null -> SolidGlassBar(Modifier.matchParentSize(), glassStyle.edgeHighlight)

            else -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(tokens.colors.surfaceSheet.copy(alpha = 0.96f)),
            )
        }
        // Like the bottom bar: icon + name when expanded, icons only when compact.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.visibleCategories.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category.id == state.selected?.id,
                    showLabel = showLabels,
                    onClick = {
                        HomeCategoryRepository.select(category.id)
                        HomeCategoryRepository.setPanelOpen(false)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    category: HomeCategory,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(NuvioTokens.Radius.md),
        color = if (selected) tokens.colors.overlaySelected else Color.Transparent,
        contentColor = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted,
        border = BorderStroke(
            width = tokens.borders.thin,
            color = if (selected) tokens.colors.borderSelected else tokens.colors.borderStrong,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = tokens.components.chipVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = category.resolvedIcon(),
                contentDescription = category.title,
                modifier = Modifier.size(20.dp),
            )
            if (showLabel) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val HomeCategoryIconKeys = listOf(
    "home", "movie", "tv", "live", "sparkle", "star", "favorite", "folder",
    "explore", "theaters", "library", "fire", "kids", "music", "games", "globe",
)

private fun homeCategoryIconFor(key: String?): ImageVector? = when (key) {
    "home" -> Icons.Rounded.Home
    "movie" -> Icons.Rounded.Movie
    "tv" -> Icons.Rounded.Tv
    "live" -> Icons.Rounded.LiveTv
    "sparkle" -> Icons.Rounded.AutoAwesome
    "star" -> Icons.Rounded.Star
    "favorite" -> Icons.Rounded.Favorite
    "folder" -> Icons.Rounded.Folder
    "explore" -> Icons.Rounded.Explore
    "theaters" -> Icons.Rounded.Theaters
    "library" -> Icons.Rounded.VideoLibrary
    "fire" -> Icons.Rounded.Whatshot
    "kids" -> Icons.Rounded.ChildCare
    "music" -> Icons.Rounded.MusicNote
    "games" -> Icons.Rounded.SportsEsports
    "globe" -> Icons.Rounded.Public
    else -> null
}

private fun HomeCategory.resolvedIcon(): ImageVector =
    homeCategoryIconFor(icon) ?: when {
        id == HOME_CATEGORY_HOME_ID -> Icons.Rounded.Home
        id == HOME_CATEGORY_MOVIES_ID -> Icons.Rounded.Movie
        id == HOME_CATEGORY_TV_ID -> Icons.Rounded.Tv
        id == HOME_CATEGORY_ANIME_ID -> Icons.Rounded.AutoAwesome
        kind == HomeCategoryKind.LiveTv -> Icons.Rounded.LiveTv
        else -> Icons.Rounded.Folder
    }

/** Always-composed host that shows the customization sheet when something requests it. */
@Composable
fun HomeCategoryEditorHost() {
    val request by HomeCategoryRepository.editorRequest.collectAsStateWithLifecycle()
    var showManager by remember { mutableStateOf(false) }
    var pickerCategoryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request) {
        val current = request ?: return@LaunchedEffect
        pickerCategoryId = current.categoryId
        showManager = true
        HomeCategoryRepository.consumeEditorRequest()
    }

    if (showManager) {
        HomeCategoryManagerSheet(
            initialPickerCategoryId = pickerCategoryId,
            pickerOnly = pickerCategoryId != null,
            onDismiss = {
                showManager = false
                pickerCategoryId = null
            },
        )
    }
}

/**
 * The full category manager, embedded in a settings page (no sheet around it). "Choose catalogs"
 * opens the catalog picker in a sheet.
 */
@Composable
fun HomeCategoryManagerEmbedded() {
    LaunchedEffect(Unit) { HomeCategoryRepository.ensureLoaded() }
    val state by HomeCategoryRepository.state.collectAsStateWithLifecycle()
    var pickerId by remember { mutableStateOf<String?>(null) }

    HomeCategoryList(
        categories = state.categories,
        onEditCatalogs = { pickerId = it },
        embedded = true,
    )
    if (pickerId != null) {
        HomeCategoryManagerSheet(
            initialPickerCategoryId = pickerId,
            pickerOnly = true,
            onDismiss = { pickerId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCategoryManagerSheet(
    initialPickerCategoryId: String?,
    pickerOnly: Boolean = false,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by HomeCategoryRepository.state.collectAsStateWithLifecycle()
    var pickerCategoryId by remember { mutableStateOf(initialPickerCategoryId) }
    val pickerCategory = state.categories.firstOrNull { it.id == pickerCategoryId }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        showDragHandle = true,
        fullHeight = true,
    ) {
        if (pickerCategory != null) {
            HomeCategoryCatalogPicker(
                category = pickerCategory,
                onBack = { if (pickerOnly) onDismiss() else pickerCategoryId = null },
            )
        } else {
            HomeCategoryList(
                categories = state.categories,
                onEditCatalogs = { pickerCategoryId = it },
            )
        }
    }
}

@Composable
private fun HomeCategoryList(
    categories: List<HomeCategory>,
    onEditCatalogs: (String) -> Unit,
    embedded: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var newTitle by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = if (embedded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        },
    ) {
        if (!embedded) {
            Text(
                text = "Navigation bar",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Choose which categories appear, their order, and which catalogs each one shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        categories.forEachIndexed { index, category ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = category.enabled,
                        onCheckedChange = { HomeCategoryRepository.setEnabled(category.id, it) },
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = category.summaryLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                    IconButton(
                        onClick = { HomeCategoryRepository.move(category.id, -1) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = tokens.colors.textPrimary)
                    }
                    IconButton(
                        onClick = { HomeCategoryRepository.move(category.id, 1) },
                        enabled = index < categories.lastIndex,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = tokens.colors.textPrimary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        renamingId = category.id
                        renameText = category.title
                    }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = tokens.colors.textMuted)
                    }
                    if (!category.builtIn) {
                        IconButton(onClick = { confirmDeleteId = category.id }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = tokens.colors.textMuted)
                        }
                    }
                    if (category.kind == HomeCategoryKind.Catalogs) {
                        TextButton(onClick = { onEditCatalogs(category.id) }) {
                            Text("Choose catalogs")
                        }
                    }
                }
                if (confirmDeleteId == category.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Delete \"${category.title}\"?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
                        TextButton(onClick = {
                            HomeCategoryRepository.delete(category.id)
                            confirmDeleteId = null
                        }) { Text("Delete") }
                    }
                }
                if (renamingId == category.id) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        HomeCategoryIconKeys.forEach { key ->
                            IconButton(onClick = { HomeCategoryRepository.setIcon(category.id, key) }) {
                                Icon(
                                    imageVector = homeCategoryIconFor(key) ?: Icons.Rounded.Folder,
                                    contentDescription = key,
                                    tint = if (category.icon == key) tokens.colors.accent else tokens.colors.textMuted,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            HomeCategoryRepository.rename(category.id, renameText)
                            renamingId = null
                        }) { Text("Save") }
                    }
                }
            }
        }

        Text(
            text = "Add category",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                singleLine = true,
                placeholder = { Text("e.g. Documentaries") },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val id = HomeCategoryRepository.addCustom(newTitle)
                    if (id != null) {
                        newTitle = ""
                        onEditCatalogs(id)
                    }
                },
                enabled = newTitle.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add", tint = tokens.colors.textPrimary)
            }
        }

        val rank = HomeCategoryRepository.state.collectAsStateWithLifecycle().value.rank
        Text(
            text = "Numbers behind posters",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
        )
        Text(
            text = "Turn numbers on per catalog with the # button in Choose catalogs.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Chip(label = "White", selected = !rank.accent, onClick = { HomeCategoryRepository.setRankAccent(false) })
            Chip(label = "Accent color", selected = rank.accent, onClick = { HomeCategoryRepository.setRankAccent(true) })
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Chip(label = "Outline", selected = !rank.filled, onClick = { HomeCategoryRepository.setRankFilled(false) })
            Chip(label = "Filled", selected = rank.filled, onClick = { HomeCategoryRepository.setRankFilled(true) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20).forEach { count ->
                Chip(
                    label = "Top $count",
                    selected = rank.count == count,
                    onClick = { HomeCategoryRepository.setRankCount(count) },
                )
            }
        }

        TextButton(
            onClick = { HomeCategoryRepository.resetToDefaults() },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun HomeCategoryCatalogPicker(
    category: HomeCategory,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val rankedKeys = HomeCategoryRepository.state.collectAsStateWithLifecycle().value.rankedKeys
    val globalLandscape = rememberPosterCardStyleUiState().catalogLandscapeModeEnabled
    val settingsState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    val allItems = settingsState.items
    val itemsByKey = remember(allItems) { allItems.associateBy { it.key } }

    // `null` (built-in Home default) means "all enabled catalogs"; materialize it for editing.
    val selectedKeys: List<String> = remember(category.catalogKeys, allItems) {
        category.catalogKeys ?: allItems.filter { it.enabled }.map { it.key }
    }
    val selectedItems = selectedKeys.mapNotNull { itemsByKey[it] }
    val selectedSet = selectedKeys.toSet()
    val availableItems = allItems.filter { it.key !in selectedSet }

    fun save(keys: List<String>) = HomeCategoryRepository.setCatalogKeys(category.id, keys)

    // Drag-to-reorder state. `localOrder` is shown while dragging (and until the saved order catches up).
    val haptics = LocalHapticFeedback.current
    val currentKeys by rememberUpdatedState(selectedKeys)
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableMapOf<String, Int>() }
    var showLegend by remember { mutableStateOf(false) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedKeys) {
        if (dragKey == null) localOrder = null
    }
    val displayItems = (localOrder?.takeIf { it.toSet() == selectedSet } ?: selectedKeys)
        .mapNotNull { itemsByKey[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, contentDescription = "Back", tint = tokens.colors.textPrimary)
            }
            Text(
                text = "Catalogs in ${category.title}",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        val canSuggest = category.id in setOf(HOME_CATEGORY_MOVIES_ID, HOME_CATEGORY_TV_ID, HOME_CATEGORY_ANIME_ID)
        if (canSuggest) {
            TextButton(onClick = { save(suggestKeysFor(category.id, allItems)) }) {
                Text("Auto-fill suggestions")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                SectionLabel("Shown in this category (${selectedItems.size})")
            }
            CompactAction(
                description = if (showLegend) "Hide help" else "Show help",
                onClick = { showLegend = !showLegend },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (showLegend) tokens.colors.accent else tokens.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showLegend) {
            Text(
                text = "Drag ☰ to reorder. Tap a row's title for options: card shape (tap to cycle default / poster / landscape), ★ Hero feeds the hero banner, # Numbers shows big numbers behind posters.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (selectedItems.isEmpty()) {
            Text(
                text = "Nothing selected yet. Tap a catalog below to add it.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        displayItems.forEach { item ->
          key(item.key) {
            val isDragging = dragKey == item.key
            val expanded = expandedKey == item.key && !isDragging
            val feedsHero = item.key !in category.heroExcludedKeys
            val ranked = item.key in rankedKeys
            val showsLandscape = item.rowShape.landscapeOverride ?: globalLandscape
            val shapeLabel = when (item.rowShape) {
                HomeRowShapeMode.DEFAULT -> "Default"
                HomeRowShapeMode.POSTER -> "Poster"
                HomeRowShapeMode.LANDSCAPE -> "Landscape"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeights[item.key] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .then(
                        if (isDragging) {
                            Modifier.background(tokens.colors.surfaceSheet, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 6.dp),
            ) {
                // Collapsed line: drag handle, title + status chips, expand chevron.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactAction(
                        description = "Drag to reorder",
                        modifier = Modifier.pointerInput(item.key) {
                            detectDragGestures(
                                onDragStart = {
                                    localOrder = currentKeys
                                    expandedKey = null
                                    dragKey = item.key
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var order = localOrder ?: currentKeys
                                    while (true) {
                                        val idx = order.indexOf(item.key)
                                        if (idx < 0) break
                                        if (dragOffset > 0f && idx < order.lastIndex) {
                                            val h = (rowHeights[order[idx + 1]] ?: break).toFloat()
                                            if (dragOffset > h / 2f) {
                                                order = order.swap(idx, idx + 1)
                                                dragOffset -= h
                                            } else break
                                        } else if (dragOffset < 0f && idx > 0) {
                                            val h = (rowHeights[order[idx - 1]] ?: break).toFloat()
                                            if (-dragOffset > h / 2f) {
                                                order = order.swap(idx, idx - 1)
                                                dragOffset += h
                                            } else break
                                        } else break
                                    }
                                    localOrder = order
                                },
                                onDragEnd = {
                                    val finalOrder = localOrder
                                    dragKey = null
                                    dragOffset = 0f
                                    if (finalOrder != null && finalOrder != currentKeys) save(finalOrder)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragCancel = {
                                    dragKey = null
                                    dragOffset = 0f
                                    localOrder = null
                                },
                            )
                        },
                        onClick = {},
                    ) {
                        Icon(Icons.Rounded.Menu, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expandedKey = if (expanded) null else item.key }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.addonName,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (feedsHero) {
                                Text(
                                    text = "  ·  ★ Hero",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.colors.accent,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (ranked) {
                                Text(
                                    text = "  ·  # Numbers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.colors.accent,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            if (item.rowShape != HomeRowShapeMode.DEFAULT) {
                                Text(
                                    text = "  ·  $shapeLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.colors.accent,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
                // Expanded: labelled controls.
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ControlPill(
                            label = shapeLabel,
                            active = item.rowShape != HomeRowShapeMode.DEFAULT,
                            modifier = Modifier.weight(1.5f),
                            onClick = {
                                HomeCatalogSettingsRepository.setRowShape(
                                    item.key,
                                    nextRowShapeMode(item.rowShape, globalLandscape),
                                )
                            },
                        ) { tint ->
                            Icon(
                                imageVector = if (showsLandscape) Icons.Rounded.CropLandscape else Icons.Rounded.CropPortrait,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        ControlPill(
                            label = "Hero",
                            active = feedsHero,
                            modifier = Modifier.weight(1f),
                            onClick = { HomeCategoryRepository.setHeroSource(category.id, item.key, !feedsHero) },
                        ) { tint ->
                            Text(text = if (feedsHero) "★" else "☆", color = tint, style = MaterialTheme.typography.labelLarge)
                        }
                        ControlPill(
                            label = "Numbers",
                            active = ranked,
                            modifier = Modifier.weight(1.3f),
                            onClick = { HomeCategoryRepository.setRanked(item.key, !ranked) },
                        ) { tint ->
                            Text(text = "#", color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        ControlPill(
                            label = null,
                            active = false,
                            modifier = Modifier.width(44.dp),
                            onClick = {
                                expandedKey = null
                                save(selectedKeys - item.key)
                            },
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Remove",
                                tint = tint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
          }
        }

        SectionLabel("Available catalogs")
        if (availableItems.isEmpty()) {
            Text(
                text = "No more catalogs available. Install addons to get more.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        availableItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { save(selectedKeys + item.key) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogLabel(item, Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = tokens.colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Tight 34dp tap target (instead of IconButton's 48dp) so all row controls fit on one line. */
@Composable
private fun CompactAction(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.nuvio.colors.textMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CatalogLabel(
    item: HomeCatalogSettingsItem,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing == null) {
            Text(
                text = item.addonName,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.addonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                trailing()
            }
        }
    }
}

/** Labelled toggle used in an expanded catalog row. Accent border/text when active, muted otherwise. */
@Composable
private fun ControlPill(
    label: String?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (Color) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val tint = if (active) tokens.colors.accent else tokens.colors.textMuted
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, tint.copy(alpha = if (active) 1f else 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading(tint)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}

private fun HomeCategory.summaryLabel(): String = when (kind) {
    HomeCategoryKind.LiveTv -> "Live TV channels"
    HomeCategoryKind.Discover -> "Discover & search"
    HomeCategoryKind.Catalogs -> when (val keys = catalogKeys) {
        null -> "All enabled catalogs"
        else -> if (keys.isEmpty()) "No catalogs selected" else "${keys.size} catalogs"
    }
}

private fun List<String>.swap(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

/**
 * Best-effort starting point for the built-in categories. Catalog keys look like
 * `<addonId>:<type>:<catalogId>`, so we match on the type segment and, for anime,
 * on the words in the key/title. The user can always edit the result.
 */
private fun suggestKeysFor(categoryId: String, items: List<HomeCatalogSettingsItem>): List<String> {
    fun HomeCatalogSettingsItem.isAnime(): Boolean {
        val haystack = (key + " " + defaultTitle + " " + addonName).lowercase()
        return "anime" in haystack || ":kitsu" in haystack || "kitsu" in haystack
    }
    return items
        .filter { !it.isCollection }
        .filter { item ->
            when (categoryId) {
                HOME_CATEGORY_MOVIES_ID -> ":movie:" in item.key && !item.isAnime()
                HOME_CATEGORY_TV_ID -> ":series:" in item.key && !item.isAnime()
                HOME_CATEGORY_ANIME_ID -> item.isAnime()
                else -> false
            }
        }
        .map { it.key }
}
