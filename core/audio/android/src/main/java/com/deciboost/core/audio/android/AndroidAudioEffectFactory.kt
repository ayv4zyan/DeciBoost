package com.deciboost.core.audio.android

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.deciboost.core.audio.policy.AudioEffectFactory
import com.deciboost.core.audio.policy.DynamicsProcessingHandle
import com.deciboost.core.audio.policy.GainTrackingHandle
import com.deciboost.core.audio.policy.LoudnessEnhancerHandle

class AndroidAudioEffectFactory : AudioEffectFactory {
    override fun createLoudnessEnhancer(sessionId: Int): LoudnessEnhancerHandle {
        return AndroidLoudnessEnhancerHandle(LoudnessEnhancer(sessionId))
    }

    override fun createDynamicsProcessing(sessionId: Int): DynamicsProcessingHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return AndroidDynamicsProcessingHandle(DynamicsProcessing(sessionId))
    }
}

private class AndroidLoudnessEnhancerHandle(
    private val enhancer: LoudnessEnhancer,
) : LoudnessEnhancerHandle, GainTrackingHandle {
    override var enabled: Boolean
        get() = enhancer.enabled
        set(value) {
            enhancer.enabled = value
        }

    override var lastGainMb: Int = 0
        private set

    override fun setTargetGain(milliBel: Int): Int {
        return try {
            enhancer.setTargetGain(milliBel)
            lastGainMb = milliBel
            AudioEffect.SUCCESS
        } catch (_: IllegalArgumentException) {
            SessionEffectRegistryCompat.ERROR_BAD_VALUE
        } catch (_: IllegalStateException) {
            AudioEffect.ERROR
        } catch (_: UnsupportedOperationException) {
            AudioEffect.ERROR
        }
    }

    override fun release() {
        enhancer.release()
    }
}

private object SessionEffectRegistryCompat {
    const val ERROR_BAD_VALUE = 2
}

private class AndroidDynamicsProcessingHandle(
    private val processing: DynamicsProcessing,
) : DynamicsProcessingHandle {
    override var enabled: Boolean
        get() = processing.enabled
        set(value) {
            processing.enabled = value
        }

    override fun setPostGain(db: Float): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val config = processing.config
                val limiter = config.getLimiterByChannelIndex(0)
                if (limiter != null) {
                    limiter.postGain = db
                    applyDynamicsConfig(processing, config)
                }
                AudioEffect.SUCCESS
            } else {
                AudioEffect.ERROR
            }
        } catch (_: IllegalArgumentException) {
            AudioEffect.ERROR
        } catch (_: IllegalStateException) {
            AudioEffect.ERROR
        } catch (_: UnsupportedOperationException) {
            AudioEffect.ERROR
        }
    }

    override fun release() {
        processing.release()
    }

    private fun applyDynamicsConfig(processing: DynamicsProcessing, config: DynamicsProcessing.Config) {
        try {
            val method = processing.javaClass.getMethod("setConfig", DynamicsProcessing.Config::class.java)
            method.invoke(processing, config)
        } catch (_: ReflectiveOperationException) {
            // Best-effort: some OEM builds apply limiter.postGain in-place without setConfig.
        }
    }
}
