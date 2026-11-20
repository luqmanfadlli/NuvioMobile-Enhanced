package com.nuvio.app.features.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

internal object PlayerEndTimeAnchor {
    var topFromBottomPx by mutableFloatStateOf(0f)
    var endFromRightPx by mutableFloatStateOf(0f)
}

internal fun Modifier.playerEndTimeAnchor(): Modifier = onGloballyPositioned { coordinates ->
    val root = coordinates.findRootCoordinates()
    val position = coordinates.positionInRoot()
    PlayerEndTimeAnchor.topFromBottomPx = root.size.height - position.y
    PlayerEndTimeAnchor.endFromRightPx = root.size.width - (position.x + coordinates.size.width)
}
