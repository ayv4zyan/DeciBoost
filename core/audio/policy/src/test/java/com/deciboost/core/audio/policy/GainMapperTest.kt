package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class GainMapperTest {

    @Test
    fun `100 percent maps to zero gain`() {
        assertEquals(0, GainMapper.toTargetGainMilliBel(BoostPercent(100)))
    }

    @Test
    fun `150 percent maps to 1500 mB`() {
        assertEquals(1500, GainMapper.toTargetGainMilliBel(BoostPercent(150)))
    }

    @Test
    fun `200 percent maps to 3000 mB max`() {
        assertEquals(3000, GainMapper.toTargetGainMilliBel(BoostPercent(200)))
    }

    @Test
    fun `dynamics gain clamps at 20 dB`() {
        assertEquals(20f, GainMapper.toDynamicsGainDb(3000), 0.01f)
    }
}
