package org.happycode.karoo.forumslader.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.io.path.exists
import kotlin.io.path.readLines

class LogcatDumperTest : ShouldSpec({

    should("dump and sanitize logs matching target PID when target PID is provided") {
        // given
        val tempDir = tempdir().toPath()
        val mockLines = sequenceOf(
            "09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:33:44:55",
            "09-03 10:00:01.000 (9999) D/OtherApp: Secret info",
            "09-03 10:00:02.000 (1234) I/FL_BLE: Device AA:BB:CC:DD:EE:FF paired"
        )
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { mockLines })

        // when
        val resultPath = dumper.dumpLogcat(targetPid = 1234)

        // then
        resultPath.shouldNotBeNull()
        resultPath.readLines() shouldContainExactly listOf(
            "09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:**:**:**",
            "09-03 10:00:02.000 (1234) I/FL_BLE: Device AA:BB:CC:**:**:** paired"
        )
    }

    should("include lines matching Forumslader tag when target PID is different") {
        // given
        val tempDir = tempdir().toPath()
        val mockLines = sequenceOf(
            "09-03 10:00:00.000 (9999) D/Forumslader: Telemetry parsed with MAC 12:34:56:78:9A:BC",
            "09-03 10:00:01.000 (9999) D/Unrelated: Unrelated message"
        )
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { mockLines })

        // when
        val resultPath = dumper.dumpLogcat(targetPid = 1234)

        // then
        resultPath.shouldNotBeNull()
        resultPath.readLines() shouldContainExactly listOf(
            "09-03 10:00:00.000 (9999) D/Forumslader: Telemetry parsed with MAC 12:34:56:**:**:**"
        )
    }

    should("include all sanitized lines when no target PID is specified") {
        // given
        val tempDir = tempdir().toPath()
        val mockLines = sequenceOf(
            "09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:33:44:55",
            "09-03 10:00:01.000 (9999) D/OtherApp: Device AA:BB:CC:DD:EE:FF paired"
        )
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { mockLines })

        // when
        val resultPath = dumper.dumpLogcat(targetPid = null)

        // then
        resultPath.shouldNotBeNull()
        resultPath.readLines() shouldContainExactly listOf(
            "09-03 10:00:00.000 (1234) D/SomeTag: Connected to 00:11:22:**:**:**",
            "09-03 10:00:01.000 (9999) D/OtherApp: Device AA:BB:CC:**:**:** paired"
        )
    }

    should("clear log file when clear is invoked") {
        // given
        val tempDir = tempdir().toPath()
        val dumper = LogcatDumper(directory = tempDir, logcatSource = { sequenceOf("Log line") })
        dumper.dumpLogcat()

        // when
        dumper.clear()

        // then
        dumper.getLogcatPath().shouldBeNull()
    }

    should("use default logcat source when none provided") {
        // given
        val tempDir = tempdir().toPath()
        val dumper = LogcatDumper(directory = tempDir)

        // when
        val result = dumper.dumpLogcat()

        // then
        result.shouldNotBeNull()
        result.exists() shouldBe true
    }

    should("use default logcat source with target PID when provided") {
        // given
        val tempDir = tempdir().toPath()
        val dumper = LogcatDumper(directory = tempDir)

        // when
        val result = dumper.dumpLogcat(targetPid = 1234)

        // then
        result.shouldNotBeNull()
        result.exists() shouldBe true
    }
})
