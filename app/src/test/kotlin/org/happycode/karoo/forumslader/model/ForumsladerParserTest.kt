package org.happycode.karoo.forumslader.model

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        val data = sentence.substring(1)
        var parity = 0
        for (char in data) {
            parity = parity xor char.code
        }
        val hex = parity.toString(16).uppercase()
        val paddedHex = if (hex.length == 1) "0$hex" else hex
        return "$sentence*$paddedHex\n"
    }

    @Test
    fun `should parse FL6 sentence when processIncomingBytes is called`() {
        // given
        // $FL6,status,gear,frequency,cell1,cell2,cell3,battCurrent,loadCurrent,...,impulseCounter
        val payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then: default wheelsize=2200, poles=14, V6 scaling (0.1x freq, 1.0x impulse)
        // batteryVoltage = (4100 + 4120 + 4110) / 1000 = 12.33
        // batteryCurrent = -150 / 1000 = -0.15
        // consumerCurrent = 250 / 1000 = 0.25
        // freq2speed = 2200 / 14 / 1000 * 0.1 = 0.01571428
        // speedMs = 100 * 0.01571428 = 1.571428
        // imp2odo = 2200 / 14 / 1000.0 * 1.0 = 0.15714
        // tripDistanceMeters = 12345 * 0.15714 = 1939.8571
        assertEquals(12.33f, result?.batteryVoltage ?: 0f, 0.01f)
        assertEquals(-0.15f, result?.batteryCurrent ?: 0f, 0.01f)
        assertEquals(0.25f, result?.consumerCurrent ?: 0f, 0.01f)
        assertEquals(1.57f, result?.speedMs ?: 0f, 0.01f)
        assertEquals(1939.93, result?.tripDistanceMeters ?: 0.0, 0.01)
        assertEquals(ForumsladerVersion.V6, parser.version)
    }

    @Test
    fun `should parse FLB sentence when processIncomingBytes is called`() {
        // given: $FLB,temperature,airPressure,altitude,slope
        val payload = $$"$FLB,228,100227,918,33"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertEquals(22.8f, result?.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(91.8f, result?.altitudeMeters ?: 0f, 0.01f)
    }

    @Test
    fun `should parse FLC sentence when processIncomingBytes is called`() {
        // given: $FLC,setnr,startCount,socState,...
        val payload = $$"$FLC,5,12,85,150,5,1000"
        val telemetry = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"
        val data = (withChecksum(payload) + withChecksum(telemetry)).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertEquals(85, result?.batteryLevelPct)
    }

    @Test
    fun `should configure wheelsize and poles when FLP sentence is processed`() {
        // given: $FLP,wheelsize,poles,...
        val configPayload = $$"$FLP,2000,10,0,0,0,0,0,1000"
        val fl6Payload = $$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"

        // when
        parser.processIncomingBytes(withChecksum(configPayload).toByteArray())
        val result = parser.processIncomingBytes(withChecksum(fl6Payload).toByteArray())

        // then: wheelsize=2000, poles=10, isV6=true
        // freq2speed = 2000 / 10 / 1000 / 10 = 0.02
        // speedMs = 100 * 0.02 = 2.0
        // imp2odo = 2000 / 10 / 1000.0 * 1.0 = 0.2
        // tripDistanceMeters = 12345 * 0.2 = 2469.0
        assertEquals(2.0f, result?.speedMs ?: 0f, 0.01f)
        assertEquals(2469.0, result?.tripDistanceMeters ?: 0.0, 0.01)
    }

    @Test
    fun `should handle split frame chunks when complete frame is received`() {
        // given
        val chunk1 = withChecksum($$"$FLB,228,100227,918,33").substring(0, 15).toByteArray()
        val chunk2 = withChecksum($$"$FLB,228,100227,918,33").substring(15).toByteArray()
        val telemetry = withChecksum($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345").toByteArray()

        // when
        val result1 = parser.processIncomingBytes(chunk1)
        val result2 = parser.processIncomingBytes(chunk2)
        assertNull(result1)
        assertNull(result2) // FLB alone doesn't trigger emission
        
        val result3 = parser.processIncomingBytes(telemetry)

        // then
        assertEquals(22.8f, result3?.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(91.8f, result3?.altitudeMeters ?: 0f, 0.01f)
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
        assertEquals(25.0f, result?.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(95.0f, result?.altitudeMeters ?: 0f, 0.01f)
        assertEquals(90, result?.batteryLevelPct)
    }

    @Test
    fun `should return null and ignore frame when checksum is invalid`() {
        // given: invalid checksum string
        val data = $$"$FLB,228,100227,918,33*FF\n".toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertNull(result)
    }

    @Test
    fun `should parse fallback FLD sentence when older frame is processed`() {
        // given: $FLD,menu,reserved,switching,frequency,voltage,battCurrent,loadCurrent,status,soc,...
        val payload = $$"$FLD,19,,0,50,12.5,0.8,0.2,-,5,0,0,0,0,10.2"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then
        assertEquals(12.5f, result?.batteryVoltage ?: 0f, 0.01f)
        assertEquals(0.8f, result?.batteryCurrent ?: 0f, 0.01f)
        assertEquals(0.2f, result?.consumerCurrent ?: 0f, 0.01f)
        assertEquals(65, result?.batteryLevelPct) // stage 5 = 65%
        assertEquals(10200.0, result?.tripDistanceMeters ?: 0.0, 0.01)
        // Assume V6 default for FLD if not detected otherwise
        assertEquals(ForumsladerVersion.Unknown, parser.version)
    }

    @Test
    fun `should parse FL5 sentence and use V5 scaling`() {
        // given
        // $FL5,status,gear,frequency,cell1,cell2,cell3,battCurrent,loadCurrent,...,impulseCounter
        val payload = $$"$FL5,0,0,100,4100,4120,4110,-150,250,0,0,0,0,12345"
        val data = withChecksum(payload).toByteArray()

        // when
        val result = parser.processIncomingBytes(data)

        // then: wheelsize=2200, poles=14, V5 scaling (1.0f freq, 4096.0 impulse)
        // freq2speed = 2200 / 14 / 1000 * 1.0 = 0.1571428
        // speedMs = 100 * 0.1571428 = 15.71428
        // imp2odo = 2200 / 14 / 1000.0 * 4096.0 = 643.6571
        // tripDistanceMeters = 12345 * 643.6571 = 7945947.43
        assertEquals(15.71f, result?.speedMs ?: 0f, 0.01f)
        assertEquals(7945947.43, result?.tripDistanceMeters ?: 0.0, 1.0)
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
        io.mockk.verify { mockConfig.wheelsize = 2100 }
        io.mockk.verify { mockConfig.poles = 28 }
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
        io.mockk.verify { mockConfig.version = ForumsladerVersion.V6 }
    }

    @Test
    fun `should track and reset config loaded status`() {
        // given
        val configPayload = "\$FLP,2000,10,0,0,0,0,0,1000"
        
        // then: initially config is not loaded
        assertEquals(false, parser.isConfigLoaded)
        
        // when: config sentence is processed
        parser.processIncomingBytes(withChecksum(configPayload).toByteArray())
        
        // then: config is loaded
        assertEquals(true, parser.isConfigLoaded)
        
        // when: reset is called
        parser.resetConfigLoaded()
        
        // then: config is reset to false
        assertEquals(false, parser.isConfigLoaded)
    }
}
