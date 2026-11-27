package org.happycode.karoo.forumslader.extension

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig

class ForumsladerDataType(
    extension: String,
    typeId: String,
    private val formatDataTypeId: String? = null
) : DataTypeImpl(extension, typeId) {

    internal var viewEmitter: ViewEmitter? = null
        private set

    internal var viewContext: Context? = null
        private set

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewEmitter = emitter
        viewContext = context

        val formatId = formatDataTypeId ?: dataTypeId
        emitter.onNext(UpdateNumericConfig(formatId))

        emitter.setCancellable {
            if (viewEmitter == emitter) {
                viewEmitter = null
                viewContext = null
            }
        }
    }
}
