package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
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
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(state.visibleCategories, key = { it.id }) { category ->
                    Chip(
                        label = category.title,
                        selected = category.id == state.selected?.id,
                        onClick = {
                            HomeCategoryRepository.select(category.id)
                            HomeCategoryRepository.setPanelOpen(false)
                        },
                    )
                }
            }
            IconButton(onClick = { HomeCategoryRepository.requestEditor() }) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Customize categories",
                    tint = tokens.colors.textPrimary,
                )
            }
        }
    }
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
            onDismiss = {
                showManager = false
                pickerCategoryId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCategoryManagerSheet(
    initialPickerCategoryId: String?,
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
                onBack = { pickerCategoryId = null },
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
) {
    val tokens = MaterialTheme.nuvio
    var newTitle by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Navigation bar",
            style = MaterialTheme.typography.titleLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
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
                        IconButton(onClick = { HomeCategoryRepository.delete(category.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = tokens.colors.textMuted)
                        }
                    }
                    if (category.kind == HomeCategoryKind.Catalogs) {
                        TextButton(onClick = { onEditCatalogs(category.id) }) {
                            Text("Choose catalogs")
                        }
                    }
                }
                if (renamingId == category.id) {
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

        SectionLabel("Shown in this category (${selectedItems.size})")
        Text(
            text = "Tap # on a catalog to show big 1–10 numbers behind its posters.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
        )
        if (selectedItems.isEmpty()) {
            Text(
                text = "Nothing selected yet. Tap a catalog below to add it.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        selectedItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogLabel(item, Modifier.weight(1f))
                val ranked = item.key in rankedKeys
                IconButton(onClick = { HomeCategoryRepository.setRanked(item.key, !ranked) }) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (ranked) tokens.colors.accent else tokens.colors.textMuted,
                    )
                }
                IconButton(
                    onClick = { save(selectedKeys.swap(index, index - 1)) },
                    enabled = index > 0,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = tokens.colors.textPrimary)
                }
                IconButton(
                    onClick = { save(selectedKeys.swap(index, index + 1)) },
                    enabled = index < selectedItems.lastIndex,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = tokens.colors.textPrimary)
                }
                IconButton(onClick = { save(selectedKeys - item.key) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = tokens.colors.textMuted)
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
private fun CatalogLabel(item: HomeCatalogSettingsItem, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = modifier.padding(vertical = 4.dp)) {
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
