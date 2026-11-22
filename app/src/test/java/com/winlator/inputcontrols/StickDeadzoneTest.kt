package com.winlator.inputcontrols

import org.junit.Assert.assertEquals
import org.junit.Test

class StickDeadzoneTest {
    @Test
    fun smallDriftIsZeroed() {
        val out = ExternalController.applyStickDeadzone(-0.06f, 0.02f, 0.10f)
        assertEquals(0f, out[0], 0f)
        assertEquals(0f, out[1], 0f)
    }

    @Test
    fun fullTiltStaysFull() {
        val out = ExternalController.applyStickDeadzone(-1f, 0f, 0.10f)
        assertEquals(-1f, out[0], 1e-5f)
    }

    @Test
    fun justPastTheDeadzoneStartsNearZero() {
        val out = ExternalController.applyStickDeadzone(0.55f, 0f, 0.10f)
        assertEquals(0.5f, out[0], 1e-5f)
    }

    @Test
    fun zeroDeadzoneLeavesValuesAlone() {
        val out = ExternalController.applyStickDeadzone(0.03f, -0.02f, 0f)
        assertEquals(0.03f, out[0], 0f)
        assertEquals(-0.02f, out[1], 0f)
    }
}
