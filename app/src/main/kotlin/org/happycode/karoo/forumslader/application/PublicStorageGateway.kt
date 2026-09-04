package org.happycode.karoo.forumslader.application

import java.nio.file.Path

interface PublicStorageGateway {
    fun exportToPublicStorage(sourcePath: Path, destinationFileName: String): Result<Path>
}
