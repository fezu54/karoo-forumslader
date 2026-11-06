package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.models.DataType

internal const val FL_EXTENSION_ID = "karoo-forumslader"
internal const val FL_ADDRESS = "AA:BB:CC:DD:EE:01"
internal const val FL_UID = "fl-$FL_ADDRESS"

internal fun flTypeId(typeId: String): String = DataType.dataTypeId(FL_EXTENSION_ID, typeId)

/** A device sentence with the XOR checksum the parser expects, framed with a trailing newline. */
internal fun bleFrame(payload: String): ByteArray {
    val checksum = payload.substring(1).fold(0) { acc, c -> acc xor c.code }
    return "$payload*${checksum.toString(16).uppercase().padStart(2, '0')}\n".toByteArray()
}
