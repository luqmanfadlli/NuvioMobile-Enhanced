package com.nuvio.app.features.settings

/**
 * A stable target for restoring the Customize Liquid Glass root list after leaving a section editor.
 * The scroll offset is kept in pixels, matching LazyListState.firstVisibleItemScrollOffset.
 */
internal data class LiquidGlassEditorScrollTarget(
    val index: Int,
    val scrollOffset: Int,
)

internal fun liquidGlassEditorScrollTarget(
    resetToTop: Boolean,
    savedIndex: Int,
    savedScrollOffset: Int,
): LiquidGlassEditorScrollTarget =
    if (resetToTop) {
        LiquidGlassEditorScrollTarget(index = 0, scrollOffset = 0)
    } else {
        LiquidGlassEditorScrollTarget(
            index = savedIndex.coerceAtLeast(0),
            scrollOffset = savedScrollOffset.coerceAtLeast(0),
        )
    }
