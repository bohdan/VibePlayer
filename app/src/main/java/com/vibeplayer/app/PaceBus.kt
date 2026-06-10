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
    // Custom setter fires goModeChanged automatically; avoids a JVM signature
    // clash that would arise from having both this property and a fun setGoMode().
    var goMode: Boolean = false
        set(value) { field = value; goModeChanged?.invoke(value) }

    @Volatile var goModeChanged: ((Boolean) -> Unit)? = null
    @Volatile var paceTierChanged: ((Int) -> Unit)? = null

    fun sendTier(tier: Int) {
        paceTierChanged?.invoke(tier)
    }
}
