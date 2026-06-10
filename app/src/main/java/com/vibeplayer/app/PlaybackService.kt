package com.vibeplayer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlin.math.sqrt

/**
 * Minimal foreground service. It produces no audio itself — the WebView's
 * Web Audio engine does — but holding foreground state plus a partial wake
 * lock prevents Android from freezing the app process (and the audio) when
 * the screen turns off or the user switches apps.
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var session: MediaSession? = null

    // ---- accelerometer / pace detection ----
    private var sensorManager: SensorManager? = null
    private var linearAccSensor: Sensor? = null

    // Rolling magnitude buffer — 30 samples @ SENSOR_DELAY_GAME ≈ 0.6 s window
    private val magBuf = FloatArray(30)
    private var magHead = 0
    private var hysTier = 0      // tier currently building hysteresis toward
    private var hysCount = 0     // consecutive readings in hysTier
    private var lastSentTier = -1

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            magBuf[magHead % magBuf.size] = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            magHead++
            // Evaluate ~3 times per second (every 15–17 samples at ~50 Hz)
            if (magHead % 16 != 0) return

            var sumSq = 0.0
            for (v in magBuf) sumSq += v * v
            val rms = sqrt(sumSq / magBuf.size).toFloat()

            val tier = when {
                rms < 0.35f -> 0   // still
                rms < 0.9f  -> 1   // slow walk
                rms < 1.8f  -> 2   // brisk walk
                rms < 3.2f  -> 3   // jog / easy bike
                rms < 5.5f  -> 4   // run / moderate bike
                rms < 8.0f  -> 5   // hard run
                else        -> 6   // sprint
            }

            // Require 4 consecutive readings (≈1.3 s) before committing to a tier change
            if (tier == hysTier) hysCount++ else { hysTier = tier; hysCount = 1 }
            if (hysCount >= 4 && tier != lastSentTier) {
                lastSentTier = tier
                PaceBus.sendTier(tier)
            }
        }
    }

    private fun registerSensor() {
        linearAccSensor?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(sensorListener)
        lastSentTier = -1
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAccSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        // When JS toggles go mode while the service is already running, register/
        // unregister the sensor immediately. If the service starts after the toggle,
        // onStartCommand checks PaceBus.goMode directly (see below).
        PaceBus.goModeChanged = { active -> if (active) registerSensor() else unregisterSensor() }

        // Receives media buttons from BT headsets (and other remote controls)
        // while the service is in the foreground, i.e. while music plays.
        session = MediaSession(this, "VibePlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = MediaCommandBus.send("play")
                override fun onPause() = MediaCommandBus.send("pause")
                override fun onSkipToNext() = MediaCommandBus.send("next")
                override fun onSkipToPrevious() = MediaCommandBus.send("prev")
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        session?.apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
            isActive = true
        }

        // If go mode was activated before the service started, begin sensing now.
        if (PaceBus.goMode) registerSensor()

        // onStartCommand can run more than once per service lifetime; creating a
        // fresh lock each time would orphan the previous one while it's still held.
        if (wakeLock?.isHeld != true) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibePlayer:playback")
                .apply { acquire() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        PaceBus.goModeChanged = null
        unregisterSensor()
        session?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 1
    }
}
