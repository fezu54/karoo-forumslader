package org.happycode.karoo.forumslader.model

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.domain.ChargeState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ForumsladerParserTest {

    private lateinit var parser: ForumsladerParser

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        parser = ForumsladerParser()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun withChecksum(sentence: String): String {
        if (!sentence.startsWith("$")) return sentence
        val checksum = sentence.substring(1)
            .fold(0) { acc, char -> acc xor char.code }
            .toString(16)
            .uppercase()
            .padStart(2, '0')
        return "$sentence*$checksum\n"
    }

    @Test
    fun `should parse FL6 sentence when processIncomingBytes is called`() {
        // given
        val payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.run {
            assertEquals(12.33f, power.batteryVoltage, 0.01f)
            assertEquals(-0.15f, power.batteryCurrent, 0.01f)
            assertEquals(0.25f, power.consumerCurrent, 0.01f)
            assertEquals(1.57f, dynamics.speedMetersPerSecond, 0.01f)
            assertEquals(1939.93, distance.tripMeters, 0.01)
        }
        assertEquals(ForumsladerVersion.V6, parser.version)
    }

    @Test
    fun `should parse FLB sentence when processIncomingBytes is called`() {
        // given
        val payload = $$"$FLB,228,100227,918,33"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.environment?.run {
            assertEquals(22.8f, temperatureCelsius, 0.01f)
            assertEquals(91.8f, altitudeMeters, 0.01f)
        }
    }

    @Test
    fun `should parse FLC sentence when processIncomingBytes is called`() {
        // given
        val payload = $$"$FLC,5,12,85,150,5,1000"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertEquals(85, result?.power?.batteryLevelPercentage)
    }

    @Test
    fun `should configure wheelsize and poles when FLP sentence is processed`() {
        // given
        val configPayload = $$"$FLP,2000,10,0,0,0,0,0,1000"
        val fl6Payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"

        // when
        parser.processIncomingBytes(withChecksum(configPayload).toByteArray())
        val result = parser.processIncomingBytes(withChecksum(fl6Payload).toByteArray())

        // then
        result?.run {
            assertEquals(2.0f, dynamics.speedMetersPerSecond, 0.01f)
            assertEquals(2469.0, distance.tripMeters, 0.01)
        }
    }

    @Test
    fun `should handle split frame chunks when complete frame is received`() {
        // given
        val flbPayload = $$"$FLB,228,100227,918,33"
        val chunk1 = withChecksum(flbPayload).substring(0, 15).toByteArray()
        val chunk2 = withChecksum(flbPayload).substring(15).toByteArray()
        val telemetry = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345").toByteArray()

        // when
        val result1 = parser.processIncomingBytes(chunk1)
        val result2 = parser.processIncomingBytes(chunk2)
        val result3 = parser.processIncomingBytes(telemetry)

        // then
        assertNull(result1)
        assertNull(result2)
        result3?.environment?.run {
            assertEquals(22.8f, temperatureCelsius, 0.01f)
            assertEquals(91.8f, altitudeMeters, 0.01f)
        }
    }

    @Test
    fun `should process multiple frames when a single chunk is processed`() {
        // given
        val frame1 = withChecksum($$"$FLB,250,100227,950,33")
        val frame2 = withChecksum($$"$FLC,5,12,90,150,5,1000")
        val frame3 = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345")
        val combinedChunk = (frame1 + frame2 + frame3).toByteArray()

        // when
        val result = parser.processIncomingBytes(combinedChunk)

        // then
        result?.run {
            assertEquals(25.0f, environment.temperatureCelsius, 0.01f)
            assertEquals(95.0f, environment.altitudeMeters, 0.01f)
            assertEquals(90, power.batteryLevelPercentage)
        }
    }

    @Test
    fun `should return null and ignore frame when checksum is invalid`() {
        // given
        val data = $$"$FLB,228,100227,918,33*FF\n".toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertNull(result)
    }

    @Test
    fun `should parse fallback FLD sentence when older frame is processed`() {
        // given
        val payload = $$"$FLD,19,,0,50,12.5,0.8,0.2,-,5,0,0,0,0,10.2"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.run {
            assertEquals(12.5f, power.batteryVoltage, 0.01f)
            assertEquals(0.8f, power.batteryCurrent, 0.01f)
            assertEquals(0.2f, power.consumerCurrent, 0.01f)
            assertEquals(65, power.batteryLevelPercentage)
            assertEquals(10200.0, distance.tripMeters, 0.01)
        }
        assertEquals(ForumsladerVersion.Unknown, parser.version)
    }

    @Test
    fun `should parse FL5 sentence and use V5 scaling when processIncomingBytes is called`() {
        // given
        val payload = $$"$FL5,0,0,100,4100,4120,4110,-150,250,0,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.run {
            assertEquals(15.71f, dynamics.speedMetersPerSecond, 0.01f)
            assertEquals(7945947.43, distance.tripMeters, 1.0)
        }
        assertEquals(ForumsladerVersion.V5, parser.version)
    }

    @Test
    fun `should save configuration to persistent storage when FLP is parsed`() {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        val parserWithConfig = ForumsladerParser(mockConfig)
        val configPayload = withChecksum($$"$FLP,2100,28,0,0,0,0,0,1000")

        // when
        parserWithConfig.processIncomingBytes(configPayload.toByteArray())

        // then
        verify {
            mockConfig.wheelsize = 2100
            mockConfig.poles = 28
        }
    }

    @Test
    fun `should save version to persistent storage when FL6 is parsed`() {
        // given
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        val parserWithConfig = ForumsladerParser(mockConfig)
        val fl6Payload = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345")

        // when
        parserWithConfig.processIncomingBytes(fl6Payload.toByteArray())

        // then
        verify { mockConfig.version = ForumsladerVersion.V6 }
    }

    @Test
    fun `should track and reset config loaded status when requested`() {
        // given
        val configPayload = $$"$FLP,2000,10,0,0,0,0,0,1000"

        // then
        assertEquals(false, parser.isConfigLoadedFlow.value)

        // when
        parser.processIncomingBytes(withChecksum(configPayload).toByteArray())

        // then
        assertEquals(true, parser.isConfigLoadedFlow.value)

        // when
        parser.resetConfigLoaded()

        // then
        assertEquals(false, parser.isConfigLoadedFlow.value)
    }

    @Test
    fun `should parse FLC set 3 for trip and tour energy when processIncomingBytes is called`() {
        // given
        val payload = $$"$FLC,3,0,123.4,45.6,0,0"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.energy?.run {
            assertEquals(123.4, tourWattHours, 0.01)
            assertEquals(45.6, tripWattHours, 0.01)
        }
    }

    @Test
    fun `should parse charge state and gear from FL6 status when processIncomingBytes is called`() {
        // given
        val payload = $$"$FL6,0x200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.run {
            assertEquals(ChargeState.CHARGING, power.chargeState)
            assertEquals(5, dynamics.generatorGear)
            assertEquals(1.233f, power.dynamoPowerWatts, 0.01f)
        }
    }

    @Test
    fun `should parse FLP for day and tour pulse offsets and compute distance when processIncomingBytes is called`() {
        // given
        val payload = $$"$FLP,2000,10,0,1000,0,2000,0,0"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.distance?.run {
            assertEquals(2269.0, dayMeters, 0.01)
            assertEquals(2069.0, tourMeters, 0.01)
            assertEquals(2469.0, odometerMeters, 0.01)
        }
    }

    @Test
    fun `should parse charge state from FL6 status when missing 0x prefix`() {
        // given
        val payload = $$"$FL6,0200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertEquals(ChargeState.CHARGING, result?.power?.chargeState)
    }

    @Test
    fun `should not overwrite battery and consumer current in FLD when missing`() {
        // given
        val fl6Payload = $$"$FL6,0200,5,100,4100,4120,4110,-150,250,0,0,0,12345"
        parser.processIncomingBytes(withChecksum(fl6Payload).toByteArray())
        val fldPayload = $$"$FLD,19,,0,50,,,,,,0,0,0,0,10.2"
        val data = withChecksum(fldPayload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        result?.power?.run {
            assertEquals(-0.15f, batteryCurrent, 0.01f)
            assertEquals(0.25f, consumerCurrent, 0.01f)
        }
    }

    @Test
    fun `should handle malformed sentences and unknown headers when processIncomingBytes is called`() {
        assertNull(parser.processIncomingBytes("FL6,0,0,0,0,0,0,0,0,0,0,0,0\n".toByteArray()))
        assertNull(parser.processIncomingBytes($$"$FLX,1,2,3*00\n".toByteArray()))
        assertNull(parser.processIncomingBytes("\n".toByteArray()))
    }

    @Test
    fun `should handle checksum errors and invalid formats when processIncomingBytes is called`() {
        val invalidChecksum = $$"$FLB,228,100227,918,33*XX\n"
        assertNull(parser.processIncomingBytes(invalidChecksum.toByteArray()))

        val missingChecksum = $$"$FLB,228,100227,918,33*\n"
        assertNull(parser.processIncomingBytes(missingChecksum.toByteArray()))

        val noChecksum = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345\n"
        val result = parser.processIncomingBytes(noChecksum.toByteArray())
        assertEquals(12.33f, result?.power?.batteryVoltage ?: 0f, 0.01f)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 5", "1, 10", "2, 20", "3, 35", "4, 50", "5, 65", "6, 80", "7, 95",
    )
    fun `should map FLD battery levels correctly`(p9: Int, expectedPct: Int) {
        val payload = $$"$FLD,19,,0,50,12.0,0,0,-,$$p9,0,0,0,0,10.0"
        val result = parser.processIncomingBytes(withChecksum(payload).toByteArray())
        assertEquals(expectedPct, result?.power?.batteryLevelPercentage)
    }

    @ParameterizedTest
    @MethodSource("chargeStateProvider")
    fun `should map FLD charge states correctly`(statusChar: String, expectedState: ChargeState) {
        val charToTest = if (statusChar == "*") "V" else statusChar
        val payload = $$"$FLD,19,,0,50,12.0,0,0,$$charToTest,5,0,0,0,0,10.0"
        val result = parser.processIncomingBytes(withChecksum(payload).toByteArray())
        assertEquals(expectedState, result?.power?.chargeState)
    }

    @ParameterizedTest
    @CsvSource(
        "0x8000, FULL",
        "0x100, DISCHARGING",
        "0x200, CHARGING"
    )
    fun `should handle FL6 status bitmask and charge states`(status: String, expectedState: ChargeState) {
        val payload = $$"$FL6,$$status,0,0,4000,4000,4000,0,0,0,0,0,0"
        val result = parser.processIncomingBytes(withChecksum(payload).toByteArray())
        assertEquals(expectedState, result?.power?.chargeState)
    }

    @ParameterizedTest
    @MethodSource("versionProvider")
    fun `should map ForumsladerVersion keys correctly`(key: String?, expected: ForumsladerVersion) {
        assertEquals(expected, ForumsladerVersion.fromKey(key))
    }

    @Test
    fun `should handle semi-colon delimiter in extractSentenceType`() {
        val payload = $$"$FLB,228,100227,918,33;41\n"
        val telemetry = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345")
        parser.processIncomingBytes(payload.toByteArray())
        val result = parser.processIncomingBytes(telemetry.toByteArray())
        assertEquals(22.8f, result?.environment?.temperatureCelsius ?: 0f, 0.01f)
    }

    @Test
    fun `should handle FL6 status as hex if not starting with 0x when processIncomingBytes is called`() {
        val payload = $$"$FL6,200,0,0,4000,4000,4000,0,0,0,0,0,0"
        assertEquals(ChargeState.CHARGING, parser.processIncomingBytes(withChecksum(payload).toByteArray())?.power?.chargeState)
    }

    @Test
    fun `should parse FLC set 5 for battery percentage when processIncomingBytes is called`() {
        val payload = $$"$FLC,5,0,77,0,0,0\n"
        val telemetry = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345")
        parser.processIncomingBytes(payload.toByteArray())
        val result = parser.processIncomingBytes(telemetry.toByteArray())
        assertEquals(77, result?.power?.batteryLevelPercentage)
    }

    @Test
    fun `should not overwrite fine-grained battery percentage from FLC with coarse values from FLD`() {
        val flcPayload = $$"$FLC,5,0,88,0,0,0\n"
        val fldPayload = $$"$FLD,19,,0,50,12.0,0,0,-,0,0,0,0,0,10.0\n" // p9=0 maps to 5%
        
        parser.processIncomingBytes(withChecksum(flcPayload).toByteArray())
        val result = parser.processIncomingBytes(withChecksum(fldPayload).toByteArray())
        
        assertEquals(88, result?.power?.batteryLevelPercentage)
    }

    @Test
    fun `should handle malformed FLC sentences when processIncomingBytes is called`() {
        val payload = $$"$FLC,UNKNOWN,0,0\n"
        assertNull(parser.processIncomingBytes(withChecksum(payload).toByteArray()))
    }

    @Test
    fun `should handle FLP with missing values when processIncomingBytes is called`() {
        val payload = $$"$FLP,2100\n"
        parser.processIncomingBytes(withChecksum(payload).toByteArray())

        val fl6 = $$"$FL6,0,0,100,4000,4000,4000,0,0,0,0,0,1000"
        val result = parser.processIncomingBytes(withChecksum(fl6).toByteArray())
        assertEquals(1.5f, result?.dynamics?.speedMetersPerSecond ?: 0f, 0.01f)
    }

    @Test
    fun `should updateConfig only when values change when processIncomingBytes is called`() {
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        every { mockConfig.wheelsize } returns 2200
        every { mockConfig.poles } returns 14
        val p = ForumsladerParser(mockConfig)

        val payload = $$"$FLP,2200,14,0,0,0,0,0,0\n"
        p.processIncomingBytes(withChecksum(payload).toByteArray())

        verify(exactly = 0) { mockConfig.wheelsize = any() }
        verify(exactly = 0) { mockConfig.poles = any() }

        val payload2 = $$"$FLP,2300,14,0,0,0,0,0,0\n"
        p.processIncomingBytes(withChecksum(payload2).toByteArray())
        verify(exactly = 1) { mockConfig.wheelsize = 2300 }
    }

    @Test
    fun `should handle non-numeric tokens in various sentences when processIncomingBytes is called`() {
        val flb = $$"$FLB,abc,def,ghi,jkl\n"
        val telemetry = withChecksum($$"$FL6,0,0,100,4000,4000,4000,0,0,0,0,0,1000")
        parser.processIncomingBytes(flb.toByteArray())
        val result = parser.processIncomingBytes(telemetry.toByteArray())
        assertEquals(0f, result?.environment?.temperatureCelsius)
        assertEquals(0f, result?.environment?.altitudeMeters)

        val flc = $$"$FLC,3,0,abc,def\n"
        parser.processIncomingBytes(flc.toByteArray())
        val result2 = parser.processIncomingBytes(telemetry.toByteArray())
        assertEquals(0.0, result2?.energy?.tourWattHours)
        assertEquals(0.0, result2?.energy?.tripWattHours)
    }

    @Test
    fun `should handle FLD p9 out of range when processIncomingBytes is called`() {
        val payload = $$"$FLD,19,,0,50,12.0,0,0,V,8,0,0,0,0,10.0"
        val result = parser.processIncomingBytes(withChecksum(payload).toByteArray())
        assertNull(result?.power?.batteryLevelPercentage)

        val payload2 = $$"$FLD,19,,0,50,12.0,0,0,V,-1,0,0,0,0,10.0"
        val result2 = parser.processIncomingBytes(withChecksum(payload2).toByteArray())
        assertNull(result2?.power?.batteryLevelPercentage)
    }

    @Test
    fun `should log failure when sentence parsing fails during processIncomingBytes`() {
        val malformed = $$"$FLX,1,2,3*00\n"
        parser.processIncomingBytes(malformed.toByteArray())
        verify { Log.d(any(), match { it.contains("Failed to parse sentence") }) }
    }

    @Test
    fun `should log configuration update when FLP is processed`() {
        val mockConfig = mockk<ForumsladerConfig>(relaxed = true)
        every { mockConfig.wheelsize } returns 2200
        every { mockConfig.poles } returns 14
        val p = ForumsladerParser(mockConfig)

        val payload = $$"$FLP,2300,14,0,0,0,0,0,0\n"
        p.processIncomingBytes(withChecksum(payload).toByteArray())

        verify { Log.i(any(), match { it.contains("Configuration updated") }) }
    }

    companion object {
        @JvmStatic
        fun chargeStateProvider(): Stream<Arguments> = Stream.of(
            Arguments.of("+", ChargeState.CHARGING),
            Arguments.of("-", ChargeState.DISCHARGING),
            Arguments.of("V", ChargeState.FULL),
            Arguments.of("*", ChargeState.FULL),
            Arguments.of("?", ChargeState.STANDBY)
        )

        @JvmStatic
        fun versionProvider(): Stream<Arguments> = Stream.of(
            Arguments.of("V5", ForumsladerVersion.V5),
            Arguments.of("V6", ForumsladerVersion.V6),
            Arguments.of("OTHER", ForumsladerVersion.Unknown),
            Arguments.of(null, ForumsladerVersion.Unknown)
        )
    }
}
