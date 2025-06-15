package com.judini.cafemode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.judini.cafemode.MainActivity
import com.judini.cafemode.R

class CafeModeService : Service() {

    private val audioSessions = HashMap<Int, AudioEffectWrapper>()
    private var isReceiverRegistered = false

    companion object {
        const val TAG = "CafeModeService"
        const val ACTION_UPDATE_SETTINGS = "com.judini.cafemode.UPDATE_SETTINGS"
        var isMasterEnabled = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service Created")
        startForeground(1, createNotification())
        registerReceivers()
        loadInitialState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(audioSessionReceiver)
        }
        audioSessions.values.forEach { it.release() }
        audioSessions.clear()
        Log.i(TAG, "Service Destroyed")
    }

    private fun loadInitialState() {
        val prefs = getSharedPreferences("cafemode_ui_state", Context.MODE_PRIVATE)
        isMasterEnabled = prefs.getBoolean("master_enabled", false)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(ACTION_UPDATE_SETTINGS)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioSessionReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(audioSessionReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)

            if (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION && sessionId != 0 && !audioSessions.containsKey(sessionId)) {
                Log.d(TAG, "New audio session: $sessionId")
                audioSessions[sessionId] = AudioEffectWrapper(sessionId)
            } else if (action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) {
                audioSessions.remove(sessionId)?.release()
                Log.d(TAG, "Closed audio session: $sessionId")
            }

            // Update all effects on any relevant action
            updateAllEffects(intent)
        }
    }

    private fun updateAllEffects(intent: Intent? = null) {
        val prefs = getSharedPreferences("cafemode_ui_state", Context.MODE_PRIVATE)

        isMasterEnabled = intent?.getBooleanExtra("isEnabled", isMasterEnabled) ?: prefs.getBoolean("master_enabled", false)
        val intensity = intent?.getFloatExtra("intensity", prefs.getFloat("intensity", 0.5f)) ?: prefs.getFloat("intensity", 0.5f)
        val spatialWidth = intent?.getFloatExtra("spatialWidth", prefs.getFloat("spatial_width", 0.5f)) ?: prefs.getFloat("spatial_width", 0.5f)

        for (session in audioSessions.values) {
            session.setEnabled(isMasterEnabled)
            if (isMasterEnabled) {
                session.setIntensity(intensity)
                session.setSpatialWidth(spatialWidth)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "CafeModeServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "CafeMode Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CafeMode Active")
            .setContentText("Processing system audio")
            .setSmallIcon(R.drawable.ic_cafe)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}