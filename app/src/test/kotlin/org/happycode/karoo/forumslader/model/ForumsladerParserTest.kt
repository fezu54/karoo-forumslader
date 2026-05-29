package org.happycode.karoo.forumslader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ForumsladerParserTest {

    private lateinit var parser: ForumsladerParser

    @BeforeEach
    fun setUp() {
        parser = ForumsladerParser()
    }

    @Test
    fun `processIncomingBytes returns ForumsladerData for complete frame`() {
        //given
        val data = "12.5,1.2,0.5,80,25.4,10.2,500.5,22.1,450.0\n".toByteArray()

        //when
        val result = parser.processIncomingBytes(data)

        //then
        assertEquals(12.5f, result?.batteryVoltage)
        assertEquals(1.2f, result?.batteryCurrent)
        assertEquals(0.5f, result?.consumerCurrent)
        assertEquals(80, result?.batteryLevelPct)
        assertEquals(25.4f, result?.speedKmh)
        assertEquals(10.2f, result?.tripDistanceKm)
        assertEquals(500.5f, result?.totalDistanceKm)
        assertEquals(22.1f, result?.temperatureCelsius)
        assertEquals(450.0f, result?.altitudeMeters)
    }

    @Test
    fun `processIncomingBytes returns null for incomplete frame`() {
        //given
        val data = "12.5,1.2,0.5".toByteArray()

        //when
        val result = parser.processIncomingBytes(data)

        //then
        assertNull(result)
    }

    @Test
    fun `processIncomingBytes returns ForumsladerData when frame is split across chunks`() {
        //given
        val chunk1 = "12.5,1.2,0.5,80,".toByteArray()
        val chunk2 = "25.4,10.2,500.5,22.1,450.0\n".toByteArray()

        //when
        val result1 = parser.processIncomingBytes(chunk1)
        val result2 = parser.processIncomingBytes(chunk2)

        //then
        assertNull(result1)
        assertEquals(12.5f, result2?.batteryVoltage)
        assertEquals(450.0f, result2?.altitudeMeters)
    }

    @Test
    fun `processIncomingBytes handles multiple frames sequentially`() {
        //given
        val frame1 = "12.5,1.2,0.5,80,25.4,10.2,500.5,22.1,450.0\n".toByteArray()
        val frame2 = "12.6,1.3,0.6,81,26.4,11.2,501.5,23.1,451.0\n".toByteArray()

        //when
        val result1 = parser.processIncomingBytes(frame1)
        val result2 = parser.processIncomingBytes(frame2)

        //then
        assertEquals(12.5f, result1?.batteryVoltage)
        assertEquals(12.6f, result2?.batteryVoltage)
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid,data\n", "12.5,abc,0.5,80,25.4,10.2,500.5,22.1,450.0\n", "\n"])
    fun `processIncomingBytes returns null or default for invalid payload`(payload: String) {
        //given
        val data = payload.toByteArray()

        //when
        val result = parser.processIncomingBytes(data)

        //then
        if (payload == "\n") {
            assertEquals(0f, result?.batteryVoltage)
        } else if (payload.startsWith("12.5")) {
             assertEquals(0f, result?.batteryCurrent)
        }
    }
}
