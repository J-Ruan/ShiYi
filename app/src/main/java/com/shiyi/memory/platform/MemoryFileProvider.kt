package com.shiyi.memory.platform

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider

/** Originals retain a private .img name; share their actual encoded image MIME type. */
class MemoryFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        if (uri.lastPathSegment?.endsWith(".img") != true) return super.getType(uri)
        return runCatching {
                openFile(uri, "r")?.use { descriptor ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
                    bounds.outMimeType
                }
            }
            .getOrNull() ?: "image/*"
    }
}
