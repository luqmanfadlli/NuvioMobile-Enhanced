package com.nuvio.app.features.catalog

import com.nuvio.app.features.home.HomeCatalogTab
import com.nuvio.app.features.home.MetaPreview

data class CatalogUiState(
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val nextSkip: Int? = null,
    val consecutiveDuplicatePages: Int = 0,
    val errorMessage: String? = null,
    val isMergedCatalog: Boolean = false,
    val mergedDisplayMode: String? = null,
    val mergedSourceTabs: List<HomeCatalogTab> = emptyList(),
    val mergedHasMore: Boolean = false,
    val failedMergedSourceKeys: Set<String> = emptySet(),
    val unavailableMergedSourceCount: Int = 0,
) {
    val canLoadMore: Boolean
        get() = if (isMergedCatalog) mergedHasMore else nextSkip != null
}

data class CatalogScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
