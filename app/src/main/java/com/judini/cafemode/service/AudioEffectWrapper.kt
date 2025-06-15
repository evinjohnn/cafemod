package com.judini.cafemode.service

import android.media.audiofx.AudioEffect
import android.util.Log
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class AudioEffectWrapper(sessionId: Int) {

    private var audioEffect: AudioEffect? = null

    companion object {
        // These UUIDs must match native-lib.cpp and audio_effects.xml
        val EFFECT_TYPE_UUID: UUID = UUID.fromString("f27317f4-c984-4de6-9a90-545759495bf2")
        val EFFECT_UUID: UUID = UUID.fromString("f2594954-a493-4794-82d6-4a52768b8aaf")

        const val PARAM_SET_ENABLED = 0
        const val PARAM_SET_INTENSITY = 1
        const val PARAM_SET_SPATIAL_WIDTH = 2
    }

    init {
        try {
            audioEffect = AudioEffect::class.java.getConstructor(
                UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).newInstance(EFFECT_TYPE_UUID, EFFECT_UUID, 0, sessionId)
            Log.i(CafeModeService.TAG, "Successfully created AudioEffect for session $sessionId")
        } catch (e: Exception) {
            Log.e(CafeModeService.TAG, "Error creating AudioEffect for session $sessionId", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            audioEffect?.enabled = enabled
            setParameter(PARAM_SET_ENABLED, if (enabled) 1 else 0)
        } catch (e: Exception) {
            Log.e(CafeModeService.TAG, "setEnabled failed", e)
        }
    }

    fun setIntensity(value: Float) = setParameter(PARAM_SET_INTENSITY, value)
    fun setSpatialWidth(value: Float) = setParameter(PARAM_SET_SPATIAL_WIDTH, value)

    fun release() {
        audioEffect?.release()
    }

    private fun setParameter(paramId: Int, value: Any) {
        if (audioEffect == null) return
        try {
            val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
            val valueBytes = when(value) {
                is Int -> ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array()
                is Float -> ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                else -> return
            }

            val setParameterMethod: Method = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
            setParameterMethod.invoke(audioEffect, paramBytes, valueBytes)
        } catch (e: Exception) {
            Log.e(CafeModeService.TAG, "setParameter failed for param $paramId", e)
        }
    }
}