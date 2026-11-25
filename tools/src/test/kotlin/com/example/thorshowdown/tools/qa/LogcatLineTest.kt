package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogcatLineTest {
    @Test
    fun LogcatLine_ThreadtimeFormat_ParsesPidLevelTagAndMessage() {
        val line = LogcatLine.parse("11-25 08:48:35.316 27843 27864 I QaCheckpoint: QA_CHECKPOINT 1 FormShown")

        assertEquals(LogcatLine(RAW_MARKER, pid = 27843, level = 'I', tag = "QaCheckpoint", message = "QA_CHECKPOINT 1 FormShown"), line)
    }

    @Test
    fun LogcatLine_TagWithSpacesBeforeColon_KeepsTagTrimmed() {
        val line = LogcatLine.parse("11-25 08:35:57.399 26648 26731 E OpenGLRenderer  : Unable to match")

        assertEquals("OpenGLRenderer", line?.tag)
    }

    @Test
    fun LogcatLine_NotThreadtime_IsNull() {
        assertNull(LogcatLine.parse("--------- beginning of main"))
    }

    @Test
    fun LogcatLine_CheckpointMarker_ParsesIndexAndName() {
        val line = LogcatLine.parse(RAW_MARKER)

        assertEquals(CheckpointMark(1, "FormShown"), line?.checkpoint())
    }

    @Test
    fun LogcatLine_OrdinaryMessage_HasNoCheckpoint() {
        val line = LogcatLine.parse("11-25 08:48:35.316 27843 27864 I AssetMoveDex: Loaded 954 moves")

        assertNull(line?.checkpoint())
    }

    private companion object {
        const val RAW_MARKER = "11-25 08:48:35.316 27843 27864 I QaCheckpoint: QA_CHECKPOINT 1 FormShown"
    }
}
