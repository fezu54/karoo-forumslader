package org.happycode.karoo.forumslader.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun `should mask last three octets when colon delimited mac address is present`() {
        //given
        val input = "Connection established to 00:11:22:33:44:55 successfully"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals("Connection established to 00:11:22:**:**:** successfully", result)
    }

    @Test
    fun `should mask last three octets when dash delimited mac address is present`() {
        //given
        val input = "Device found: AA-BB-CC-DD-EE-FF"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals("Device found: AA-BB-CC-**-**-**", result)
    }

    @Test
    fun `should handle lowercase mac address correctly`() {
        //given
        val input = "Connecting to 0a:1b:2c:3d:4e:5f"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals("Connecting to 0a:1b:2c:**:**:**", result)
    }

    @Test
    fun `should mask multiple mac addresses in a single string`() {
        //given
        val input = "Disconnected from 12:34:56:78:9A:BC, reconnecting to DE:AD:BE:EF:00:01 now"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals("Disconnected from 12:34:56:**:**:**, reconnecting to DE:AD:BE:**:**:** now", result)
    }

    @Test
    fun `should leave string unchanged when no mac address is present`() {
        //given
        val input = "FLB: temp=23.5°C alt=150.2m speed=25.4km/h"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals(input, result)
    }

    @Test
    fun `should not mask invalid mac address with wrong number of octets`() {
        //given
        val input = "Invalid addresses: 00:11:22:33:44 and 00:11:22:33:44:55:66"

        //when
        val result = LogSanitizer.sanitize(input)

        //then
        assertEquals(input, result)
    }
}
