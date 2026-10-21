package com.nuvio.app.features.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.forNavigationMaterial
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassFrostedBar
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.SolidGlassBar
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.resolveNavigationGlass
import com.nuvio.app.features.home.HOME_CATEGORY_ANIME_ID
import com.nuvio.app.features.home.HOME_CATEGORY_HOME_ID
import com.nuvio.app.features.home.HOME_CATEGORY_MOVIES_ID
import com.nuvio.app.features.home.HOME_CATEGORY_TV_ID
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCategory
import com.nuvio.app.features.home.HomeCategoryKind
import com.nuvio.app.features.home.HomeCategoryRepository
import com.nuvio.app.features.home.HomeRowShapeMode
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * The full category manager, embedded in a settings page (no sheet around it). The catalog
 * chooser opens in a sheet.
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

// ---------------------------------------------------------------------------
// Drag-to-reorder list (drag handle on the left of every row)
// ---------------------------------------------------------------------------

/**
 * A column whose rows can be reordered by dragging the handle on the left of each row.
 * [row] receives the handle modifier and must attach it to its drag handle. [onReorder] is
 * called once when a drag ends, with the index the row came from and the index it moved to.
 */
@Composable
private fun <T> DragReorderColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onReorder: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, index: Int, dragHandle: Modifier) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val latestItems by rememberUpdatedState(items)
    val latestReorder by rememberUpdatedState(onReorder)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPx by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(1f) }

    fun finishDrag() {
        val key = dragKey ?: return
        val distance = dragPx
        dragKey = null
        dragPx = 0f
        val list = latestItems
        val from = list.indexOfFirst { keyOf(it) == key }
        if (from < 0) return
        val steps = (distance / rowHeightPx.coerceAtLeast(1f)).roundToInt()
        val to = (from + steps).coerceIn(0, list.lastIndex)
        if (to != from) latestReorder(from, to)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val key = keyOf(item)
            val dragging = dragKey == key
            val handle = Modifier.pointerInput(key) {
                detectDragGestures(
                    onDragStart = {
                        dragKey = key
                        dragPx = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragPx += amount.y
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { finishDrag() },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, if (dragging) dragPx.roundToInt() else 0) }
                    .onGloballyPositioned { rowHeightPx = it.size.height.toFloat() }
                    .background(if (dragging) tokens.colors.surfaceSheet else Color.Transparent),
            ) {
                row(item, index, handle)
            }
            if (index != items.lastIndex) {
                HorizontalDivider(color = tokens.colors.borderStrong.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun DragHandleIcon(title: String, dragHandle: Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = dragHandle
            .size(36.dp)
            .semantics { contentDescription = "Drag to reorder $title" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Visualizer
// ---------------------------------------------------------------------------

/** One catalog row as it will appear on Home. */
private data class PreviewRowSpec(
    val title: String,
    val landscape: Boolean,
    val hero: Boolean,
    val ranked: Boolean,
)

/** The navigation bar as it will look, with the enabled categories in their order. */
@Composable
private fun NavigationPreview(enabledCategories: List<HomeCategory>) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
        )
        Text(
            text = if (enabledCategories.isEmpty()) {
                "No categories are on. Turn one on to see it here."
            } else {
                "This is the navigation bar, in this order."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.colors.surfaceSheet.copy(alpha = 0.6f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            enabledCategories.forEachIndexed { index, category ->
                CategoryChip(
                    category = category,
                    selected = index == 0,
                    showLabel = true,
                    onClick = {},
                )
            }
        }
    }
}

/** A category's rows as they will appear on Home: one strip per catalog, in order. */
@Composable
private fun CategoryLayoutPreview(categoryTitle: String, rows: List<PreviewRowSpec>) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
        )
        Text(
            text = if (rows.isEmpty()) {
                "Add catalogs to see how this category will look."
            } else {
                "Each catalog is a row on Home, in this order."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.colors.surfaceSheet.copy(alpha = 0.6f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            rows.forEach { spec -> PreviewRowStrip(spec) }
        }
    }
}

@Composable
private fun PreviewRowStrip(spec: PreviewRowSpec) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (spec.hero) {
                Text(text = "★", color = tokens.colors.accent, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(PREVIEW_CARD_COUNT) { index ->
                PreviewCard(
                    landscape = spec.landscape,
                    rank = if (spec.ranked) index + 1 else null,
                    highlight = spec.hero && index == 0,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(landscape: Boolean, rank: Int?, highlight: Boolean) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(8.dp)
    val width = if (landscape) 112.dp else 72.dp
    val height = if (landscape) 63.dp else 108.dp
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(tokens.colors.overlaySelected)
            .border(
                BorderStroke(1.dp, if (highlight) tokens.colors.accent else tokens.colors.borderStrong),
                shape,
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

private const val PREVIEW_CARD_COUNT = 4

// ---------------------------------------------------------------------------
// Category manager
// ---------------------------------------------------------------------------

@Composable
private fun HomeCategoryList(
    categories: List<HomeCategory>,
    onEditCatalogs: (String) -> Unit,
    embedded: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var newTitle by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<HomeCategory?>(null) }
    var confirmDelete by remember { mutableStateOf<HomeCategory?>(null) }

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
            text = "Turn categories on or off, drag the handle to reorder, and pick the catalogs each one shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )

        NavigationPreview(enabledCategories = categories.filter { it.enabled })

        DragReorderColumn(
            items = categories,
            keyOf = { it.id },
            onReorder = { from, to -> moveCategory(categories, from, to) },
            row = { category, index, dragHandle ->
                CategoryRow(
                    category = category,
                    dragHandle = dragHandle,
                    onEditCatalogs = { onEditCatalogs(category.id) },
                    onEdit = { editing = category },
                    onDelete = { confirmDelete = category },
                )
            },
        )

        Text(
            text = "Add category",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
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

        NumbersSettings()

        TextButton(
            onClick = { HomeCategoryRepository.resetToDefaults() },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Reset to defaults")
        }
    }

    editing?.let { category ->
        RenameCategoryDialog(category = category, onDismiss = { editing = null })
    }
    confirmDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete category?") },
            text = { Text("\"${category.title}\" will be removed from the navigation bar.") },
            confirmButton = {
                TextButton(onClick = {
                    HomeCategoryRepository.delete(category.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/** Moves a category from [from] to [to] using the repository's one-step move. */
private fun moveCategory(categories: List<HomeCategory>, from: Int, to: Int) {
    val category = categories.getOrNull(from) ?: return
    val delta = to - from
    repeat(abs(delta)) {
        HomeCategoryRepository.move(category.id, if (delta > 0) 1 else -1)
    }
}

@Composable
private fun CategoryRow(
    category: HomeCategory,
    dragHandle: Modifier,
    onEditCatalogs: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var menuOpen by remember { mutableStateOf(false) }
    val summary = category.summaryLabel()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandleIcon(title = category.title, dragHandle = dragHandle)
        Switch(
            checked = category.enabled,
            onCheckedChange = { HomeCategoryRepository.setEnabled(category.id, it) },
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = tokens.colors.textPrimary)) {
                    append(category.title)
                }
                withStyle(SpanStyle(color = tokens.colors.textMuted)) {
                    append("  ·  $summary")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options for ${category.title}",
                    tint = tokens.colors.textPrimary,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                if (category.kind == HomeCategoryKind.Catalogs) {
                    DropdownMenuItem(
                        text = { Text("Choose catalogs") },
                        onClick = {
                            menuOpen = false
                            onEditCatalogs()
                        },
                    )
                }
                if (!category.builtIn) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameCategoryDialog(
    category: HomeCategory,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var title by remember(category.id) { mutableStateOf(category.title) }
    var icon by remember(category.id) { mutableStateOf(category.icon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textMuted,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    HomeCategoryIconKeys.forEach { key ->
                        IconButton(onClick = { icon = key }) {
                            Icon(
                                imageVector = homeCategoryIconFor(key) ?: Icons.Rounded.Folder,
                                contentDescription = key,
                                tint = if (icon == key) tokens.colors.accent else tokens.colors.textMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotEmpty() && trimmed != category.title) {
                        HomeCategoryRepository.rename(category.id, trimmed)
                    }
                    val chosenIcon = icon
                    if (chosenIcon != null && chosenIcon != category.icon) {
                        HomeCategoryRepository.setIcon(category.id, chosenIcon)
                    }
                    onDismiss()
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NumbersSettings() {
    val tokens = MaterialTheme.nuvio
    val state by HomeCategoryRepository.state.collectAsStateWithLifecycle()
    val rank = state.rank

    Text(
        text = "Numbers on posters",
        style = MaterialTheme.typography.titleSmall,
        color = tokens.colors.textPrimary,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
    )
    Text(
        text = "Turn them on per catalog with the # in the catalog settings.",
        style = MaterialTheme.typography.bodySmall,
        color = tokens.colors.textMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(label = "White", selected = !rank.accent, onClick = { HomeCategoryRepository.setRankAccent(false) })
        Chip(label = "Accent", selected = rank.accent, onClick = { HomeCategoryRepository.setRankAccent(true) })
        Chip(label = "Outline", selected = !rank.filled, onClick = { HomeCategoryRepository.setRankFilled(false) })
        Chip(label = "Filled", selected = rank.filled, onClick = { HomeCategoryRepository.setRankFilled(true) })
        listOf(5, 10, 20).forEach { count ->
            Chip(
                label = "Top $count",
                selected = rank.count == count,
                onClick = { HomeCategoryRepository.setRankCount(count) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Catalog chooser
// ---------------------------------------------------------------------------

@Composable
private fun HomeCategoryCatalogPicker(
    category: HomeCategory,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val state by HomeCategoryRepository.state.collectAsStateWithLifecycle()
    val rankedKeys = state.rankedKeys
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
    val visibleKeys = selectedItems.map { it.key }
    val selectedSet = selectedKeys.toSet()
    val availableItems = allItems.filter { it.key !in selectedSet }

    // Only one catalog row is open at a time.
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var renamingKey by remember { mutableStateOf<String?>(null) }

    fun save(keys: List<String>) = HomeCategoryRepository.setCatalogKeys(category.id, keys)

    // Keys that are saved but not in the catalog list stay at the end, untouched.
    fun saveOrder(orderedVisible: List<String>) {
        val hidden = selectedKeys.filterNot { it in visibleKeys.toSet() }
        save(orderedVisible + hidden)
    }

    val previewRows = selectedItems.map { item ->
        PreviewRowSpec(
            title = item.displayTitle,
            landscape = item.rowShape.landscapeOverride ?: globalLandscape,
            hero = item.key !in category.heroExcludedKeys,
            ranked = item.key in rankedKeys,
        )
    }

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
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${selectedItems.size} catalogs",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
        }

        val canSuggest = category.id in setOf(HOME_CATEGORY_MOVIES_ID, HOME_CATEGORY_TV_ID, HOME_CATEGORY_ANIME_ID)
        if (canSuggest) {
            OutlinedButton(
                onClick = { save(suggestKeysFor(category.id, allItems)) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Auto-fill suggestions")
            }
        }

        CategoryLayoutPreview(categoryTitle = category.title, rows = previewRows)

        SectionLabel("In this category")
        Text(
            text = "Drag the handle to reorder. Tap a title to adjust it.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
        )
        if (selectedItems.isEmpty()) {
            Text(
                text = "Nothing selected yet. Add catalogs from the list below.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        DragReorderColumn(
            items = selectedItems,
            keyOf = { it.key },
            onReorder = { from, to -> saveOrder(visibleKeys.moved(from, to)) },
            row = { item, _, dragHandle ->
                val expanded = expandedKey == item.key
                CatalogRow(
                    item = item,
                    expanded = expanded,
                    feedsHero = item.key !in category.heroExcludedKeys,
                    ranked = item.key in rankedKeys,
                    shape = item.rowShape,
                    globalLandscape = globalLandscape,
                    dragHandle = dragHandle,
                    onToggle = { expandedKey = if (expanded) null else item.key },
                    onHeroChange = { HomeCategoryRepository.setHeroSource(category.id, item.key, it) },
                    onRankedChange = { HomeCategoryRepository.setRanked(item.key, it) },
                    onShapeChange = { HomeCatalogSettingsRepository.setRowShape(item.key, it) },
                    onRename = { renamingKey = item.key },
                    onRemove = { save(selectedKeys - item.key) },
                )
            },
        )

        renamingKey?.let { key ->
            itemsByKey[key]?.let { item ->
                CatalogRenameDialog(item = item, onDismiss = { renamingKey = null })
            }
        }

        SectionLabel("Available")
        if (availableItems.isEmpty()) {
            Text(
                text = "No more catalogs. Install addons to get more.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        availableItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { save(selectedKeys + item.key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.addonName,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add ${item.displayTitle}",
                    tint = tokens.colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (index != availableItems.lastIndex) {
                HorizontalDivider(color = tokens.colors.borderStrong.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun CatalogRow(
    item: HomeCatalogSettingsItem,
    expanded: Boolean,
    feedsHero: Boolean,
    ranked: Boolean,
    shape: HomeRowShapeMode,
    globalLandscape: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onHeroChange: (Boolean) -> Unit,
    onRankedChange: (Boolean) -> Unit,
    onShapeChange: (HomeRowShapeMode) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val badges = buildList {
        if (feedsHero) add("Hero")
        if (ranked) add("Numbers")
        when (shape) {
            HomeRowShapeMode.DEFAULT -> Unit
            HomeRowShapeMode.POSTER -> add("Poster")
            HomeRowShapeMode.LANDSCAPE -> add("Landscape")
        }
    }
    val subtitle = (listOf(item.addonName) + badges)
        .filter { it.isNotBlank() }
        .joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandleIcon(title = item.displayTitle, dragHandle = dragHandle)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CatalogOverflowMenu(title = item.displayTitle, onRename = onRename, onRemove = onRemove)
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CatalogSettingLine(
                    title = "Hero banner",
                    description = "Use this catalog for the hero banner in this category",
                    checked = feedsHero,
                    onCheckedChange = onHeroChange,
                )
                CatalogSettingLine(
                    title = "Big numbers",
                    description = "Show rank numbers behind posters",
                    checked = ranked,
                    onCheckedChange = onRankedChange,
                )

                Text(
                    text = "Card shape",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textPrimary,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        label = if (globalLandscape) "Default (landscape)" else "Default (poster)",
                        selected = shape == HomeRowShapeMode.DEFAULT,
                        onClick = { onShapeChange(HomeRowShapeMode.DEFAULT) },
                    )
                    Chip(
                        label = "Poster",
                        selected = shape == HomeRowShapeMode.POSTER,
                        onClick = { onShapeChange(HomeRowShapeMode.POSTER) },
                    )
                    Chip(
                        label = "Landscape",
                        selected = shape == HomeRowShapeMode.LANDSCAPE,
                        onClick = { onShapeChange(HomeRowShapeMode.LANDSCAPE) },
                    )
                }

            }
        }
    }
}

@Composable
private fun CatalogOverflowMenu(
    title: String,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options for $title",
                tint = tokens.colors.textPrimary,
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Remove from category",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun CatalogRenameDialog(
    item: HomeCatalogSettingsItem,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var name by remember(item.key) { mutableStateOf(item.displayTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename catalog") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Clear the name to use \"${item.defaultTitle}\" again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                HomeCatalogSettingsRepository.setCustomTitle(item.key, name.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CatalogSettingLine(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

private fun HomeCategory.summaryLabel(): String = when (kind) {
    HomeCategoryKind.LiveTv -> "Live TV channels"
    HomeCategoryKind.Discover -> "Discover & search"
    HomeCategoryKind.Catalogs -> when (val keys = catalogKeys) {
        null -> "All enabled catalogs"
        else -> if (keys.isEmpty()) "No catalogs selected" else "${keys.size} catalogs"
    }
}

/** Returns a copy with the item at [from] moved to [to]. Out-of-range indices return the list as is. */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
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
