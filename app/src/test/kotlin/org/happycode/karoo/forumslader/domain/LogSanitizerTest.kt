package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class LogSanitizerTest : ShouldSpec({

    should("mask last three octets when colon-delimited MAC address is present") {
        // given
        val input = "Connection established to 00:11:22:33:44:55 successfully"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "Connection established to 00:11:22:**:**:** successfully"
    }

    should("mask last three octets when dash-delimited MAC address is present") {
        // given
        val input = "Device found: AA-BB-CC-DD-EE-FF"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "Device found: AA-BB-CC-**-**-**"
    }

    should("mask lowercase MAC address when present") {
        // given
        val input = "Connecting to 0a:1b:2c:3d:4e:5f"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "Connecting to 0a:1b:2c:**:**:**"
    }

    should("mask mixed-case MAC address when present") {
        // given
        val input = "Connecting to 0A:1b:2C:3d:4E:5f"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "Connecting to 0A:1b:2C:**:**:**"
    }

    should("mask multiple MAC addresses when multiple are present in a single string") {
        // given
        val input = "Disconnected from 12:34:56:78:9A:BC, reconnecting to DE:AD:BE:EF:00:01 now"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "Disconnected from 12:34:56:**:**:**, reconnecting to DE:AD:BE:**:**:** now"
    }

    should("mask standalone MAC address when located at string boundaries") {
        // given
        val input = "00:11:22:33:44:55"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe "00:11:22:**:**:**"
    }

    should("leave string unchanged when no MAC address is present") {
        // given
        val input = "FLB: temp=23.5°C alt=150.2m speed=25.4km/h"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe input
    }

    should("not mask invalid MAC address when it has wrong number of octets") {
        // given
        val input = "Invalid addresses: 00:11:22:33:44 and 00:11:22:33:44:55:66"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe input
    }

    should("not mask MAC address when embedded inside a longer hex string") {
        // given
        val input = "Addresses: FF00:11:22:33:44:55 and 00:11:22:33:44:5566"

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe input
    }

    should("return empty string when input is empty") {
        // given
        val input = ""

        // when
        val result = LogSanitizer.sanitize(input)

        // then
        result shouldBe ""
    }
})
