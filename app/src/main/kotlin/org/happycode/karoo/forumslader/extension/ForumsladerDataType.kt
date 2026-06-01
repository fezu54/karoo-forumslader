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
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        formatDataTypeId?.let {
            emitter.onNext(UpdateNumericConfig(formatDataTypeId = it))
        }
    }
}
