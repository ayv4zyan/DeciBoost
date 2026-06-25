package com.deciboost.testing.fakes

import com.deciboost.core.audio.policy.DynamicsProcessingHandle

class FakeDynamicsProcessing(val sessionId: Int) : DynamicsProcessingHandle {
    var released = false
        private set

    override var enabled: Boolean = false
    var lastPostGainDb: Float = 0f
        private set

    override fun setPostGain(db: Float): Int {
        if (released) return -1
        lastPostGainDb = db
        return 0
    }

    override fun release() {
        released = true
        enabled = false
    }
}