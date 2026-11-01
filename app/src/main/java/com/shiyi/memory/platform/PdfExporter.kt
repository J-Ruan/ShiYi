package com.shiyi.memory.platform

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.shiyi.memory.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfOptions(
    val title: String = "我们的回忆册",
    val cover: String = "",
    val images: Boolean = true,
    val text: Boolean = true,
    val dates: Boolean = true,
)

class PdfExporter(private val context: Context, private val repo: MemoryRepository) {
    suspend fun export(uri: Uri, records: List<Memory>, snapshot: Snapshot, options: PdfOptions) =
        withContext(Dispatchers.IO) {
            val pdf = PdfDocument()
            var page: PdfDocument.Page? = null
            var y = 48f
            var number = 0
            val ink =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(52, 50, 58)
                    textSize = 12f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
            fun newPage() {
                page?.let { old ->
                    old.canvas.drawText(
                        "拾忆 · ${number}",
                        48f,
                        814f,
                        TextPaint(ink).apply { textSize = 9f },
                    )
                    pdf.finishPage(old)
                }
                number++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                page!!.canvas.drawColor(Color.rgb(250, 248, 245))
                y = 48f
            }
            fun text(value: String, size: Float = 12f, space: Float = 14f) {
                if (value.isBlank()) return
                ink.textSize = size
                val layout =
                    StaticLayout.Builder.obtain(value, 0, value.length, ink, 499)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1f)
                        .setIncludePad(false)
                        .build()
                for (line in 0 until layout.lineCount) {
                    val top = layout.getLineTop(line)
                    val bottom = layout.getLineBottom(line)
                    val h = (bottom - top).toFloat()
                    if (y + h > 780) newPage()
                    val canvas = page!!.canvas
                    canvas.save()
                    canvas.clipRect(48f, y, 547f, y + h)
                    canvas.translate(48f, y - top)
                    layout.draw(canvas)
                    canvas.restore()
                    y += h
                }
                y += space
            }
            fun image(path: String, maximumHeight: Float = 430f) {
                if (path.isBlank()) return
                if (!repo.file(path).isFile) {
                    text("[图片缺失]", 11f)
                    return
                }
                repo.images.withOriginal(path) { file ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    val orientation =
                        runCatching {
                                ExifInterface(file.path)
                                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                            }
                            .getOrDefault(1)
                            .takeIf { it in 1..8 } ?: 1
                    // Missing/undefined EXIF orientation is normal for screenshots.
                    // Read long screenshots in bounded regions and paginate at readable page width.
                    // A cover remains a single proportional image; content screenshots are not
                    // shrunk
                    // to a thin strip.
                    if (
                        maximumHeight == 430f &&
                            bounds.outWidth > 0 &&
                            bounds.outHeight.toLong() > bounds.outWidth.toLong() * 2 &&
                            orientation == 1
                    ) {
                        @Suppress("DEPRECATION")
                        val region =
                            runCatching { BitmapRegionDecoder.newInstance(file.path, false) }
                                .getOrNull()
                        if (region != null) {
                            try {
                                var sample = 1
                                while (bounds.outWidth / sample > 1400) sample *= 2
                                val stripHeight =
                                    (680f * bounds.outWidth / 499f).toInt().coerceAtLeast(1)
                                var top = 0
                                while (top < bounds.outHeight) {
                                    val bottom = minOf(bounds.outHeight, top + stripHeight)
                                    val bitmap =
                                        requireNotNull(
                                            region.decodeRegion(
                                                Rect(0, top, bounds.outWidth, bottom),
                                                BitmapFactory.Options().apply {
                                                    inSampleSize = sample
                                                },
                                            )
                                        ) {
                                            "无法读取长截图"
                                        }
                                    try {
                                        val height = bitmap.height * 499f / bitmap.width
                                        if (y + height > 780) newPage()
                                        page!!
                                            .canvas
                                            .drawBitmap(
                                                bitmap,
                                                null,
                                                RectF(48f, y, 547f, y + height),
                                                Paint(
                                                    Paint.ANTI_ALIAS_FLAG or
                                                        Paint.FILTER_BITMAP_FLAG
                                                ),
                                            )
                                        y += height + 18
                                    } finally {
                                        bitmap.recycle()
                                    }
                                    top = bottom
                                }
                            } finally {
                                region.recycle()
                            }
                            return@withOriginal
                        }
                    }
                    val decoded =
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            android.graphics.ImageDecoder.decodeBitmap(
                                android.graphics.ImageDecoder.createSource(file)
                            ) { decoder, info, _ ->
                                val ratio =
                                    (maxOf(info.size.width, info.size.height) / 1600f)
                                        .coerceAtLeast(1f)
                                decoder.setTargetSize(
                                    (info.size.width / ratio).toInt(),
                                    (info.size.height / ratio).toInt(),
                                )
                                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, bounds)
                            val factor =
                                (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1)
                            BitmapFactory.decodeFile(
                                file.path,
                                BitmapFactory.Options().apply { inSampleSize = factor },
                            )
                        }
                    if (decoded == null) {
                        text("[无法解码图片]", 11f)
                        return@withOriginal
                    }
                    val bitmap =
                        if (android.os.Build.VERSION.SDK_INT < 28) {
                            val orientation =
                                runCatching {
                                        ExifInterface(file.path)
                                            .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                                    }
                                    .getOrDefault(1)
                            val matrix = Matrix()
                            when (orientation) {
                                2 -> matrix.setScale(-1f, 1f)
                                3 -> matrix.setRotate(180f)
                                4 -> matrix.setScale(1f, -1f)
                                5 -> {
                                    matrix.setRotate(90f)
                                    matrix.postScale(-1f, 1f)
                                }
                                6 -> matrix.setRotate(90f)
                                7 -> {
                                    matrix.setRotate(-90f)
                                    matrix.postScale(-1f, 1f)
                                }
                                8 -> matrix.setRotate(-90f)
                            }
                            if (orientation in 2..8)
                                Bitmap.createBitmap(
                                        decoded,
                                        0,
                                        0,
                                        decoded.width,
                                        decoded.height,
                                        matrix,
                                        true,
                                    )
                                    .also { if (it !== decoded) decoded.recycle() }
                            else decoded
                        } else decoded
                    try {
                        val ratio = minOf(499f / bitmap.width, maximumHeight / bitmap.height)
                        val w = bitmap.width * ratio
                        val h = bitmap.height * ratio
                        if (y + h > 780) newPage()
                        page!!
                            .canvas
                            .drawBitmap(
                                bitmap,
                                null,
                                RectF(48f + (499 - w) / 2, y, 48f + (499 + w) / 2, y + h),
                                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                            )
                        y += h + 18
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            val temp = File.createTempFile("memory-book-", ".pdf", context.cacheDir)
            try {
                newPage()
                y = 120f
                text(options.title, 30f, 28f)
                text("MEMORY  /  私人的时光收藏", 11f, 30f)
                image(options.cover, 420f)
                text("${records.size} 段回忆 · 本地生成", 12f)
                newPage()
                text("目录", 24f)
                records.sortedBy { it.date }.forEach { text("${it.date}  ${it.title}", 12f) }
                var currentMonth = ""
                records
                    .filter { it.deletedAt == null }
                    .sortedBy { it.date }
                    .forEach { memory ->
                        if (memory.date.take(7) != currentMonth) {
                            currentMonth = memory.date.take(7)
                            newPage()
                            y = 220f
                            text("${currentMonth.take(4)} 年 ${currentMonth.takeLast(2)} 月", 28f)
                            newPage()
                        }
                        if (y + 110 > 780) newPage() else y += 18
                        text(memory.title, 21f, 12f)
                        if (options.dates)
                            text(
                                listOf(memory.date, memory.time, memory.location)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                10f,
                            )
                        if (options.text) {
                            text(memory.description)
                            if (memory.excerpt.isNotBlank()) text("聊天摘录\n${memory.excerpt}")
                            if (memory.context.isNotBlank()) text("聊天背景\n${memory.context}")
                            if (memory.reason.isNotBlank()) text("收藏理由\n${memory.reason}")
                            if (memory.feeling.isNotBlank()) text("个人感受\n${memory.feeling}")
                        }
                        if (options.images)
                            snapshot.media
                                .filter { it.memoryId == memory.id }
                                .sortedBy { it.position }
                                .forEach { image(it.path) }
                    }
                page?.let {
                    it.canvas.drawText(
                        "拾忆 · $number",
                        48f,
                        814f,
                        TextPaint(ink).apply { textSize = 9f },
                    )
                    pdf.finishPage(it)
                    page = null
                }
                temp.outputStream().use(pdf::writeTo)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                }
            } finally {
                pdf.close()
                temp.delete()
            }
        }
}
