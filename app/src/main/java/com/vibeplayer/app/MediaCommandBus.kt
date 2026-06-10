package com.vibeplayer.app

/**
 * Hands media-button commands ("play", "pause", "next", "prev") from
 * PlaybackService's MediaSession to whatever is hosting the WebView.
 */
object MediaCommandBus {
    @Volatile
    var listener: ((String) -> Unit)? = null

    fun send(command: String) {
        listener?.invoke(command)
    }
}
