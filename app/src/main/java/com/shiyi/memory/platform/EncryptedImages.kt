package com.shiyi.memory.platform

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.shiyi.memory.data.ImageStorage
import java.io.Closeable
import okio.Path.Companion.toOkioPath

/** Coil closes and deletes each authenticated temporary original after decoding. No disk cache. */
data class EncryptedImage(val path: String)

class EncryptedImageFetcher(private val path: String, private val storage: ImageStorage) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val temp = storage.temporary(path)
        return SourceResult(
            ImageSource(temp.toOkioPath(), closeable = Closeable { temp.delete() }),
            null,
            DataSource.DISK,
        )
    }

    class Factory(private val storage: ImageStorage) : Fetcher.Factory<EncryptedImage> {
        override fun create(
            data: EncryptedImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EncryptedImageFetcher(data.path, storage)
    }
}
