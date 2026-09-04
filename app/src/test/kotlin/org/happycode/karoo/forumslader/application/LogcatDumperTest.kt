package org.happycode.karoo.forumslader.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines

class LogcatDumperTest {

    @Test
    fun `should dump and sanitize logs matching target pid`() {
        //given
        val tempDir = createTempDirectory()
        val mockLines = sequenceOf(
            "09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:33:44:55",
            "09-03 10:00:01.000 (9999) D/OtherApp: Secret info",
            "09-03 10:00:02.000 (1234) I/FL_BLE: Device AA:BB:CC:DD:EE:FF paired"
        )
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { mockLines })

        //when
        val resultPath = dumper.dumpLogcat(targetPid = 1234)

        //then
        assertNotNull(resultPath)
        val lines = resultPath.readLines()
        assertEquals(2, lines.size)
        assertEquals("09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:**:**:**", lines[0])
        assertEquals("09-03 10:00:02.000 (1234) I/FL_BLE: Device AA:BB:CC:**:**:** paired", lines[1])
    }

    @Test
    fun `should include lines matching Forumslader tag even without matching pid`() {
        //given
        val tempDir = createTempDirectory()
        val mockLines = sequenceOf(
            "09-03 10:00:00.000 (9999) D/Forumslader: Telemetry parsed with MAC 12:34:56:78:9A:BC",
            "09-03 10:00:01.000 (9999) D/Unrelated: Unrelated message"
        )
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { mockLines })

        //when
        val resultPath = dumper.dumpLogcat(targetPid = 1234)

        //then
        val lines = resultPath.readLines()
        assertEquals(1, lines.size)
        assertEquals("09-03 10:00:00.000 (9999) D/Forumslader: Telemetry parsed with MAC 12:34:56:**:**:**", lines[0])
    }

    @Test
    fun `should clear log file when clear invoked`() {
        //given
        val tempDir = createTempDirectory()
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { sequenceOf("Log line") })
        dumper.dumpLogcat()

        //when
        dumper.clear()

        //then
        assertNull(dumper.getLogcatPath())
    }

    @Test
    fun `should use default logcat source when none provided`() {
        //given
        val tempDir = createTempDirectory()
        val dumper = LogcatDumper(directory = tempDir)

        //when
        val result = dumper.dumpLogcat()

        //then
        assertNotNull(result)
    }
}
