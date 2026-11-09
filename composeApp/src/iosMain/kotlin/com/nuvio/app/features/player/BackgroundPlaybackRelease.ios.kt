package com.nuvio.app.features.player

internal actual fun closeBackgroundPlayerForNewPlayback() {
    NuvioPlayerBridgeFactory.closeParkedPictureInPicture()
}
