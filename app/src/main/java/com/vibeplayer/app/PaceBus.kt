package com.vibeplayer.app

/**
 * Two-way relay between the JS bridge (caller) and PlaybackService (sensor host).
 *
 * MainActivity → Service:  setGoMode(active) flips goMode and fires goModeChanged
 *                           so the service can register/unregister the sensor.
 * Service → MainActivity:  sendTier(tier) fires paceTierChanged so the activity
 *                           can forward the tier into the WebView.
 */
object PaceBus {
    @Volatile var goMode: Boolean = false
    @Volatile var goModeChanged: ((Boolean) -> Unit)? = null
    @Volatile var paceTierChanged: ((Int) -> Unit)? = null

    fun setGoMode(active: Boolean) {
        goMode = active
        goModeChanged?.invoke(active)
    }

    fun sendTier(tier: Int) {
        paceTierChanged?.invoke(tier)
    }
}
