package com.vibeplayer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Minimal foreground service. It produces no audio itself — the WebView's
 * Web Audio engine does — but holding foreground state plus a partial wake
 * lock prevents Android from freezing the app process (and the audio) when
 * the screen turns off or the user switches apps.
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

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
