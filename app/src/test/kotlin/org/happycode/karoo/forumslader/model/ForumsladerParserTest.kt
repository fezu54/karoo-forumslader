package org.happycode.karoo.forumslader.model

import android.util.Log
import io.mockk.every
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

        // then: default wheelsize=2200, poles=14, isV6=true
        // batteryVoltage = (4100 + 4120 + 4110) / 1000 = 12.33
        // batteryCurrent = -150 / 1000 = -0.15
        // consumerCurrent = 250 / 1000 = 0.25
        // freq2speed = 2200 / 14 * 0.0036 / 10 = 0.05657143
        // speedKmh = 100 * 0.05657143 = 5.657143
        // imp2odo = 2200 / 14 / 1000000.0 * 1.0 = 0.00015714
        // tripDistanceKm = 12345 * 0.00015714 = 1.9398571
        assertEquals(12.33f, result?.batteryVoltage ?: 0f, 0.01f)
        assertEquals(-0.15f, result?.batteryCurrent ?: 0f, 0.01f)
        assertEquals(0.25f, result?.consumerCurrent ?: 0f, 0.01f)
        assertEquals(5.66f, result?.speedKmh ?: 0f, 0.01f)
        assertEquals(1.94f, result?.tripDistanceKm ?: 0f, 0.01f)
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
        // freq2speed = 2000 / 10 * 0.0036 / 10 = 0.072
        // speedKmh = 100 * 0.072 = 7.2
        // imp2odo = 2000 / 10 / 1000000.0 * 1.0 = 0.0002
        // tripDistanceKm = 12345 * 0.0002 = 2.469
        assertEquals(7.20f, result?.speedKmh ?: 0f, 0.01f)
        assertEquals(2.47f, result?.tripDistanceKm ?: 0f, 0.01f)
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
        assertEquals(10.2f, result?.tripDistanceKm ?: 0f, 0.01f)
    }
}
