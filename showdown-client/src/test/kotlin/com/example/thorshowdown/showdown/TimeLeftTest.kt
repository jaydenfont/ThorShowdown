package com.example.thorshowdown.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeLeftTest {
    @Test
    fun TimeLeft_RealTimerMessage_ParsesTurnAndTotal() {
        val timeLeft = TimeLeft.parse("Time left: 150 sec this turn | 150 sec total")

        assertEquals(TimeLeft(turnSeconds = 150, totalSeconds = 150), timeLeft)
    }

    @Test
    fun TimeLeft_DifferentTurnAndTotal_KeepsBoth() {
        val timeLeft = TimeLeft.parse("Time left: 90 sec this turn | 240 sec total")

        assertEquals(TimeLeft(turnSeconds = 90, totalSeconds = 240), timeLeft)
    }

    @Test
    fun TimeLeft_TimerOnAnnouncement_IsNotTimeLeft() {
        assertNull(TimeLeft.parse("Battle timer is ON: inactive players will automatically lose when time's up."))
    }

    @Test
    fun TimeLeft_OpponentWarning_IsNotTimeLeft() {
        assertNull(TimeLeft.parse("bdvonpdslmv has 60 seconds left."))
    }

    @Test
    fun TimeLeft_MissingNumbers_IsNotTimeLeft() {
        assertNull(TimeLeft.parse("Time left: sec this turn | sec total"))
    }
}
