package org.happycode.karoo.forumslader.adapters.ble

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.DfuState
import java.net.URI

class NordicDfuAdapterTest : StringSpec({

    lateinit var adapter: NordicDfuAdapter

    beforeTest {
        adapter = NordicDfuAdapter()
    }

    "should start in Idle state" {
        adapter.state.value shouldBe DfuState.Idle
    }

    "should change state to Connecting when startUpdate is called" {
        // given
        val uri = URI("file:///test.hex")

        // when
        adapter.startUpdate(uri)

        // then
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should change state to Idle when cancelUpdate is called" {
        // given
        adapter.startUpdate(URI("file:///test.hex"))
        
        // when
        adapter.cancelUpdate()

        // then
        adapter.state.value shouldBe DfuState.Idle
    }
})
