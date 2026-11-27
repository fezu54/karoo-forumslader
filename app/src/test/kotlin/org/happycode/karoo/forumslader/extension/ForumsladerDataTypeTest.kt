package org.happycode.karoo.forumslader.extension

import android.content.Context
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

private const val EXTENSION_ID = "karoo-forumslader"

class ForumsladerDataTypeTest : ShouldSpec({

    var context = mockk<Context>(relaxed = true)
    var config = mockk<ViewConfig>(relaxed = true)
    var emitter = mockk<ViewEmitter>(relaxed = true)

    beforeEach {
        context = mockk(relaxed = true)
        config = mockk(relaxed = true)
        emitter = mockk(relaxed = true)
    }

    afterEach {
        unmockkAll()
    }

    should("emit UpdateNumericConfig for standard fields when startView is executed") {
        // given
        val dataType = ForumsladerDataType(
            extension = EXTENSION_ID,
            typeId = "fl_battery_voltage",
            formatDataTypeId = null
        )
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, config, emitter)
        cancelSlot.captured()

        // then
        verify { emitter.onNext(UpdateNumericConfig(dataType.dataTypeId)) }
        dataType.viewEmitter shouldBe null
        dataType.viewContext shouldBe null
    }

    should("use formatDataTypeId when provided for standard fields on startView execution") {
        // given
        val formatId = DataType.Type.POWER
        val dataType = ForumsladerDataType(EXTENSION_ID, "fl_dynamo_power", formatId)

        // when
        dataType.startView(context, config, emitter)

        // then
        verify { emitter.onNext(UpdateNumericConfig(formatId)) }
    }
})
