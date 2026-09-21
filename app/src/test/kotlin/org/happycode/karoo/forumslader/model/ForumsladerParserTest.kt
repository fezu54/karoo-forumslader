package org.happycode.karoo.forumslader.model

import android.util.Log
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.domain.ChargeState

class ForumsladerParserTest : ShouldSpec({

    lateinit var parser: ForumsladerParser

    beforeEach {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        parser = ForumsladerParser()
    }

    afterEach {
        unmockkAll()
    }

    fun String.withChecksum(): String {
        if (!startsWith("$")) return this
        val checksum = substring(1)
            .fold(0) { acc, char -> acc xor char.code }
            .toString(16)
            .uppercase()
            .padStart(2, '0')
        return "$this*$checksum\n"
    }

    fun String.toFrameBytes(): ByteArray = withChecksum().toByteArray()

    should("parse FL6 sentence when processIncomingBytes is called") {
        // given
        val payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = payload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.run {
            power.batteryVoltage shouldBe (12.33f plusOrMinus 0.01f)
            power.batteryCurrent shouldBe (-0.15f plusOrMinus 0.01f)
            power.consumerCurrent shouldBe (0.25f plusOrMinus 0.01f)
            dynamics.speedMetersPerSecond shouldBe (1.57f plusOrMinus 0.01f)
            distance.tripMeters shouldBe (1939.93 plusOrMinus 0.01)
        }
        parser.version shouldBe ForumsladerVersion.V6
    }

    should("parse FLB sentence when processIncomingBytes is called") {
        // given
        val payload = $$"$FLB,228,100227,918,33"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (payload.withChecksum() + telemetry.withChecksum()).toByteArray()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.environment.run {
            temperatureCelsius shouldBe (22.8f plusOrMinus 0.01f)
            altitudeMeters shouldBe (91.8f plusOrMinus 0.01f)
        }
    }

    should("parse FLC sentence when processIncomingBytes is called") {
        // given
        val payload = $$"$FLC,5,12,85,150,5,1000"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (payload.withChecksum() + telemetry.withChecksum()).toByteArray()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.power.batteryLevelPercentage shouldBe 85
    }

    should("configure wheelsize and poles when FLP sentence is processed") {
        // given
        val configPayload = $$"$FLP,2000,10,0,0,0,0,0,1000"
        val fl6Payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"

        // when
        parser.processIncomingBytes(configPayload.toFrameBytes())
        val result = parser.processIncomingBytes(fl6Payload.toFrameBytes()).shouldNotBeNull()

        // then
        result.run {
            dynamics.speedMetersPerSecond shouldBe (2.0f plusOrMinus 0.01f)
            distance.tripMeters shouldBe (2469.0 plusOrMinus 0.01)
        }
    }

    should("handle split frame chunks when complete frame is received") {
        // given
        val flbPayload = $$"$FLB,228,100227,918,33"
        val chunk1 = flbPayload.withChecksum().substring(0, 15).toByteArray()
        val chunk2 = flbPayload.withChecksum().substring(15).toByteArray()
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345".toFrameBytes()

        // when
        val result1 = parser.processIncomingBytes(chunk1)
        val result2 = parser.processIncomingBytes(chunk2)
        val result3 = parser.processIncomingBytes(telemetry).shouldNotBeNull()

        // then
        result1.shouldBeNull()
        result2.shouldBeNull()
        result3.environment.run {
            temperatureCelsius shouldBe (22.8f plusOrMinus 0.01f)
            altitudeMeters shouldBe (91.8f plusOrMinus 0.01f)
        }
    }

    should("process multiple frames when a single chunk is processed") {
        // given
        val frame1 = $$"$FLB,250,100227,950,33".withChecksum()
        val frame2 = $$"$FLC,5,12,90,150,5,1000".withChecksum()
        val frame3 = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345".withChecksum()
        val combinedChunk = (frame1 + frame2 + frame3).toByteArray()

        // when
        val result = parser.processIncomingBytes(combinedChunk).shouldNotBeNull()

        // then
        result.run {
            environment.temperatureCelsius shouldBe (25.0f plusOrMinus 0.01f)
            environment.altitudeMeters shouldBe (95.0f plusOrMinus 0.01f)
            power.batteryLevelPercentage shouldBe 90
        }
    }

    should("return null and ignore frame when checksum is invalid") {
        // given
        val data = $$"$FLB,228,100227,918,33*FF\n".toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result.shouldBeNull()
    }

    should("parse fallback FLD sentence when older frame is processed") {
        // given
        val payload = $$"$FLD,19,,0,50,12.5,0.8,0.2,-,5,0,0,0,0,10.2"
        val data = payload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.run {
            power.batteryVoltage shouldBe (12.5f plusOrMinus 0.01f)
            power.batteryCurrent shouldBe (0.8f plusOrMinus 0.01f)
            power.consumerCurrent shouldBe (0.2f plusOrMinus 0.01f)
            power.batteryLevelPercentage shouldBe 65
            distance.tripMeters shouldBe (10200.0 plusOrMinus 0.01)
        }
        parser.version shouldBe ForumsladerVersion.Unknown
    }

    should("parse FL5 sentence and use V5 scaling when processIncomingBytes is called") {
        // given
        val payload = $$"$FL5,0,0,100,4100,4120,4110,-150,250,0,0,0,0,12345"
        val data = payload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.run {
            dynamics.speedMetersPerSecond shouldBe (15.71f plusOrMinus 0.01f)
            distance.tripMeters shouldBe (7945947.43 plusOrMinus 1.0)
        }
        parser.version shouldBe ForumsladerVersion.V5
    }

    should("save configuration to persistent storage when FLP is parsed") {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        val parserWithConfig = ForumsladerParser(config = mockConfig)
        val configPayload = $$"$FLP,2100,28,0,0,0,0,0,1000".toFrameBytes()

        // when
        parserWithConfig.processIncomingBytes(configPayload)

        // then
        verify {
            mockConfig.wheelsize = 2100
            mockConfig.poles = 28
        }
    }

    should("save version to persistent storage when FL6 is parsed") {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        val parserWithConfig = ForumsladerParser(config = mockConfig)
        val fl6Payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345".toFrameBytes()

        // when
        parserWithConfig.processIncomingBytes(fl6Payload)

        // then
        verify { mockConfig.version = ForumsladerVersion.V6 }
    }

    should("track and reset config loaded status when requested") {
        // given
        val configPayload = $$"$FLP,2000,10,0,0,0,0,0,1000"

        // then
        parser.isConfigLoadedFlow.value shouldBe false

        // when
        parser.processIncomingBytes(configPayload.toFrameBytes())

        // then
        parser.isConfigLoadedFlow.value shouldBe true

        // when
        parser.resetConfigLoaded()

        // then
        parser.isConfigLoadedFlow.value shouldBe false
    }

    should("parse FLC set 3 for trip and tour energy when processIncomingBytes is called") {
        // given
        val payload = $$"$FLC,3,0,123.4,45.6,0,0"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (payload.withChecksum() + telemetry.withChecksum()).toByteArray()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.energy.run {
            tourWattHours shouldBe (123.4 plusOrMinus 0.01)
            tripWattHours shouldBe (45.6 plusOrMinus 0.01)
        }
    }

    should("parse charge state and gear from FL6 status when processIncomingBytes is called") {
        // given
        val payload = $$"$FL6,0x200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = payload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.run {
            power.chargeState shouldBe ChargeState.CHARGING
            dynamics.generatorGear shouldBe 5
            power.dynamoPowerWatts shouldBe (1.233f plusOrMinus 0.01f)
        }
    }

    should("parse FLP for day and tour pulse offsets and compute distance when processIncomingBytes is called") {
        // given
        val payload = $$"$FLP,2000,10,0,1000,0,2000,0,0"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (payload.withChecksum() + telemetry.withChecksum()).toByteArray()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.distance.run {
            dayMeters shouldBe (2269.0 plusOrMinus 0.01)
            tourMeters shouldBe (2069.0 plusOrMinus 0.01)
            odometerMeters shouldBe (2469.0 plusOrMinus 0.01)
        }
    }

    should("parse charge state from FL6 status when missing 0x prefix") {
        // given
        val payload = $$"$FL6,0200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = payload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.power.chargeState shouldBe ChargeState.CHARGING
    }

    should("not overwrite battery and consumer current in FLD when missing") {
        // given
        val fl6Payload = $$"$FL6,0200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        parser.processIncomingBytes(fl6Payload.toFrameBytes())
        val fldPayload = $$"$FLD,19,,0,50,,,,,,0,0,0,0,10.2"
        val data = fldPayload.toFrameBytes()

        // when
        val result = parser.processIncomingBytes(data).shouldNotBeNull()

        // then
        result.power.run {
            batteryCurrent shouldBe (-0.15f plusOrMinus 0.01f)
            consumerCurrent shouldBe (0.25f plusOrMinus 0.01f)
        }
    }

    should("handle malformed sentences and unknown headers when processIncomingBytes is called") {
        // given / when / then
        parser.processIncomingBytes("FL6,0,0,0,0,0,0,0,0,0,0,0,0\n".toByteArray()).shouldBeNull()
        parser.processIncomingBytes($$"$FLX,1,2,3*00\n".toByteArray()).shouldBeNull()
        parser.processIncomingBytes("\n".toByteArray()).shouldBeNull()
    }

    should("handle checksum errors and invalid formats when processIncomingBytes is called") {
        // given
        val invalidChecksum = $$"$FLB,228,100227,918,33*XX\n".toByteArray()
        val missingChecksum = $$"$FLB,228,100227,918,33*\n".toByteArray()
        val noChecksum = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345\n".toByteArray()

        // when / then
        parser.processIncomingBytes(invalidChecksum).shouldBeNull()
        parser.processIncomingBytes(missingChecksum).shouldBeNull()

        val result = parser.processIncomingBytes(noChecksum).shouldNotBeNull()
        result.power.batteryVoltage shouldBe (12.33f plusOrMinus 0.01f)
    }

    listOf(
        0 to 5, 1 to 10, 2 to 20, 3 to 35, 4 to 50, 5 to 65, 6 to 80, 7 to 95
    ).forEach { (p9, expectedPct) ->
        should("map FLD battery level for p9=$p9 correctly") {
            // given
            val payload = $$"$FLD,19,,0,50,12.0,0,0,-,$$p9,0,0,0,0,10.0"

            // when
            val result = parser.processIncomingBytes(payload.toFrameBytes()).shouldNotBeNull()

            // then
            result.power.batteryLevelPercentage shouldBe expectedPct
        }
    }

    listOf(
        "+" to ChargeState.CHARGING,
        "-" to ChargeState.DISCHARGING,
        "V" to ChargeState.FULL,
        "*" to ChargeState.FULL,
        "?" to ChargeState.STANDBY
    ).forEach { (statusChar, expectedState) ->
        should("map FLD charge state for '$statusChar' correctly") {
            // given
            val payload = $$"$FLD,19,,0,50,12.0,0,0,$$statusChar,5,0,0,0,0,10.0"

            // when
            val result = parser.processIncomingBytes(payload.toFrameBytes()).shouldNotBeNull()

            // then
            result.power.chargeState shouldBe expectedState
        }
    }

    listOf(
        "0x8000" to ChargeState.FULL,
        "0x100" to ChargeState.DISCHARGING,
        "0x200" to ChargeState.CHARGING
    ).forEach { (status, expectedState) ->
        should("handle FL6 status bitmask $status and charge states") {
            // given
            val payload = $$"$FL6,$$status,0,0,4000,4000,4000,0,0,0,0,0,0"

            // when
            val result = parser.processIncomingBytes(payload.toFrameBytes()).shouldNotBeNull()

            // then
            result.power.chargeState shouldBe expectedState
        }
    }

    listOf(
        "V5" to ForumsladerVersion.V5,
        "V6" to ForumsladerVersion.V6,
        "OTHER" to ForumsladerVersion.Unknown,
        null to ForumsladerVersion.Unknown
    ).forEach { (key, expected) ->
        should("map ForumsladerVersion key '$key' correctly") {
            // when / then
            ForumsladerVersion.fromKey(key) shouldBe expected
        }
    }

    should("handle semi-colon delimiter in extractSentenceType") {
        // given
        val payload = $$"$FLB,228,100227,918,33;41\n"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345".withChecksum()

        // when
        parser.processIncomingBytes(payload.toByteArray())
        val result = parser.processIncomingBytes(telemetry.toByteArray()).shouldNotBeNull()

        // then
        result.environment.temperatureCelsius shouldBe (22.8f plusOrMinus 0.01f)
    }

    should("handle FL6 status as hex if not starting with 0x when processIncomingBytes is called") {
        // given
        val payload = $$"$FL6,200,0,0,4000,4000,4000,0,0,0,0,0,0"

        // when
        val result = parser.processIncomingBytes(payload.toFrameBytes()).shouldNotBeNull()

        // then
        result.power.chargeState shouldBe ChargeState.CHARGING
    }

    should("parse FLC set 5 for battery percentage when processIncomingBytes is called") {
        // given
        val payload = $$"$FLC,5,0,77,0,0,0\n"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345".withChecksum()

        // when
        parser.processIncomingBytes(payload.toByteArray())
        val result = parser.processIncomingBytes(telemetry.toByteArray()).shouldNotBeNull()

        // then
        result.power.batteryLevelPercentage shouldBe 77
    }

    should("not overwrite fine-grained battery percentage from FLC with coarse values from FLD") {
        // given
        val flcPayload = $$"$FLC,5,0,88,0,0,0\n"
        val fldPayload = $$"$FLD,19,,0,50,12.0,0,0,-,0,0,0,0,0,10.0\n" // p9=0 maps to 5%

        // when
        parser.processIncomingBytes(flcPayload.toFrameBytes())
        val result = parser.processIncomingBytes(fldPayload.toFrameBytes()).shouldNotBeNull()

        // then
        result.power.batteryLevelPercentage shouldBe 88
    }

    should("handle malformed FLC sentences when processIncomingBytes is called") {
        // given
        val payload = $$"$FLC,UNKNOWN,0,0\n"

        // when
        val result = parser.processIncomingBytes(payload.toFrameBytes())

        // then
        result.shouldBeNull()
    }

    should("handle FLP with missing values when processIncomingBytes is called") {
        // given
        val payload = $$"$FLP,2100\n"
        val fl6 = $$"$FL6,0,0,100,4000,4000,4000,0,0,0,0,0,1000"

        // when
        parser.processIncomingBytes(payload.toFrameBytes())
        val result = parser.processIncomingBytes(fl6.toFrameBytes()).shouldNotBeNull()

        // then
        result.dynamics.speedMetersPerSecond shouldBe (1.5f plusOrMinus 0.01f)
    }

    should("updateConfig only when values change when processIncomingBytes is called") {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        every { mockConfig.wheelsize } returns 2200
        every { mockConfig.poles } returns 14
        val p = ForumsladerParser(config = mockConfig)

        val payload1 = $$"$FLP,2200,14,0,0,0,0,0,0\n"
        val payload2 = $$"$FLP,2300,14,0,0,0,0,0,0\n"

        // when
        p.processIncomingBytes(payload1.toFrameBytes())

        // then
        verify(exactly = 0) { mockConfig.wheelsize = any() }
        verify(exactly = 0) { mockConfig.poles = any() }

        // when
        p.processIncomingBytes(payload2.toFrameBytes())

        // then
        verify(exactly = 1) { mockConfig.wheelsize = 2300 }
    }

    should("handle non-numeric tokens in various sentences when processIncomingBytes is called") {
        // given
        val flb = $$"$FLB,abc,def,ghi,jkl\n"
        val flc = $$"$FLC,3,0,abc,def\n"
        val telemetry = $$"$FL6,0,0,100,4000,4000,4000,0,0,0,0,0,1000".toFrameBytes()

        // when
        parser.processIncomingBytes(flb.toByteArray())
        val result1 = parser.processIncomingBytes(telemetry).shouldNotBeNull()

        // then
        result1.environment.temperatureCelsius shouldBe 0f
        result1.environment.altitudeMeters shouldBe 0f

        // when
        parser.processIncomingBytes(flc.toByteArray())
        val result2 = parser.processIncomingBytes(telemetry).shouldNotBeNull()

        // then
        result2.energy.tourWattHours shouldBe 0.0
        result2.energy.tripWattHours shouldBe 0.0
    }

    should("handle FLD p9 out of range when processIncomingBytes is called") {
        // given
        val payload1 = $$"$FLD,19,,0,50,12.0,0,0,V,8,0,0,0,0,10.0"
        val payload2 = $$"$FLD,19,,0,50,12.0,0,0,V,-1,0,0,0,0,10.0"

        // when
        val result1 = parser.processIncomingBytes(payload1.toFrameBytes()).shouldNotBeNull()
        val result2 = parser.processIncomingBytes(payload2.toFrameBytes()).shouldNotBeNull()

        // then
        result1.power.batteryLevelPercentage.shouldBeNull()
        result2.power.batteryLevelPercentage.shouldBeNull()
    }

    should("log failure when sentence parsing fails during processIncomingBytes") {
        // given
        val malformed = $$"$FLX,1,2,3*00\n"

        // when
        parser.processIncomingBytes(malformed.toByteArray())

        // then
        verify { Log.d(any(), match { it.contains("Failed to parse sentence") }) }
    }

    should("log configuration update when FLP is processed") {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        every { mockConfig.wheelsize } returns 2200
        every { mockConfig.poles } returns 14
        val p = ForumsladerParser(config = mockConfig)

        val payload = $$"$FLP,2300,14,0,0,0,0,0,0\n"

        // when
        p.processIncomingBytes(payload.toFrameBytes())

        // then
        verify { Log.i(any(), match { it.contains("Configuration updated") }) }
    }
})
