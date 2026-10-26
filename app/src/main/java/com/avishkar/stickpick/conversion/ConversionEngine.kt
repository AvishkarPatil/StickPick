package com.avishkar.stickpick.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.avishkar.stickpick.data.local.PackStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class ConversionResult(val file: File, val isAnimated: Boolean)

class ConversionEngine(context: Context) {
    private val storage = PackStorage(context)

    /**
     * Converts a sticker file. For .webm/.mp4:
     * 1. Try animated WebP via FFmpeg
     * 2. If fails → try static WebP from extracted frame
     * 3. If both fail → skip (Result.failure)
     *
     * Returns ConversionResult with isAnimated flag so pack can be built correctly.
     */
    suspend fun convertSticker(
        inputFile: File,
        packId: String,
        outputName: String
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        try {
            val outDir = storage.getConvertedDir(packId)
            val outputFile = File(outDir, "$outputName.webp")
            outputFile.delete()

            when (inputFile.extension.lowercase()) {
                "webm", "mp4" -> {
                    // Try 1: Animated WebP via FFmpeg
                    if (convertVideoViaFFmpeg(inputFile, outputFile) && isAnimatedWebp(outputFile)) {
                        return@withContext Result.success(ConversionResult(outputFile, true))
                    }

                    // Try 2: Static WebP from extracted frame
                    outputFile.delete()
                    if (convertVideoFrameToStatic(inputFile, outputFile)) {
                        return@withContext Result.success(ConversionResult(outputFile, false))
                    }

                    // Both failed — skip
                    outputFile.delete()
                    return@withContext Result.failure(Exception("Cannot convert: ${inputFile.name}"))
                }
                else -> {
                    convertImage(inputFile, outputFile)
                    if (outputFile.exists() && outputFile.length() > 100) {
                        return@withContext Result.success(ConversionResult(outputFile, false))
                    }
                    return@withContext Result.failure(Exception("Cannot convert: ${inputFile.name}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertVideoViaFFmpeg(input: File, output: File): Boolean {
        // Note: color=0x00000000 = fully transparent padding
        val commands = listOf(
            "-y -i \"${input.absolutePath}\" " +
                    "-vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,fps=10\" " +
                    "-t 3 -an -loop 0 -quality 50 -compression_level 4 -vcodec libwebp_anim " +
                    "\"${output.absolutePath}\"",
            "-y -i \"${input.absolutePath}\" " +
                    "-vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,fps=10\" " +
                    "-t 3 -an -loop 0 -quality 50 " +
                    "\"${output.absolutePath}\"",
            "-y -i \"${input.absolutePath}\" " +
                    "-vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,fps=5\" " +
                    "-t 2 -an -loop 0 -quality 30 " +
                    "\"${output.absolutePath}\""
        )

        for (cmd in commands) {
            output.delete()
            FFmpegKit.execute(cmd)
            if (output.exists() && output.length() > 200) {
                if (output.length() > 500_000) shrinkAnimated(input, output)
                return true
            }
        }
        return false
    }

    private fun convertVideoFrameToStatic(input: File, output: File): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(input.absolutePath)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            if (frame == null) return false

            val scaled = scaleTo512Transparent(frame)
            writeWebp(scaled, output)
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
            output.exists() && output.length() > 100
        } catch (e: Exception) {
            false
        }
    }

    private fun isAnimatedWebp(file: File): Boolean {
        if (!file.exists() || file.length() < 30) return false
        return try {
            val raf = RandomAccessFile(file, "r")
            val header = ByteArray(30)
            raf.read(header)
            raf.close()
            val content = String(header, Charsets.ISO_8859_1)
            if (!content.startsWith("RIFF") || !content.contains("WEBP")) return false
            if (content.contains("VP8X")) {
                val flagsByte = header[20].toInt() and 0xFF
                return (flagsByte and 0x02) != 0
            }
            val raf2 = RandomAccessFile(file, "r")
            val scanBuf = ByteArray(minOf(file.length(), 4096).toInt())
            raf2.read(scanBuf)
            raf2.close()
            String(scanBuf, Charsets.ISO_8859_1).contains("ANIM")
        } catch (e: Exception) {
            false
        }
    }

    private fun shrinkAnimated(input: File, output: File) {
        val cmd = "-y -i \"${input.absolutePath}\" " +
                "-vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,fps=8\" " +
                "-t 2 -an -loop 0 -quality 25 -compression_level 6 " +
                "\"${output.absolutePath}\""
        FFmpegKit.execute(cmd)
    }

    private fun convertImage(input: File, output: File) {
        // If input is already webp, try direct resize via Bitmap first (preserves quality)
        val bitmap = BitmapFactory.decodeFile(input.absolutePath)
        if (bitmap != null) {
            val scaled = scaleTo512Transparent(bitmap)
            writeWebp(scaled, output)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            if (output.exists() && output.length() > 100) return
        }

        // Fallback to FFmpeg
        val cmd = "-y -i \"${input.absolutePath}\" " +
                "-vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000\" " +
                "-quality 80 \"${output.absolutePath}\""
        FFmpegKit.execute(cmd)
    }

    private fun scaleTo512Transparent(bitmap: Bitmap): Bitmap {
        val maxSize = 512
        val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(maxSize, maxSize, Bitmap.Config.ARGB_8888)
        // Transparent background — no drawColor needed, ARGB_8888 defaults to transparent
        val canvas = Canvas(result)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(scaled, (maxSize - newW) / 2f, (maxSize - newH) / 2f, null)
        if (scaled !== bitmap) scaled.recycle()
        return result
    }

    @Suppress("DEPRECATION")
    private fun writeWebp(bitmap: Bitmap, output: File, quality: Int = 80) {
        FileOutputStream(output).use { fos ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, fos)
            }
        }
    }

    suspend fun createTrayImage(inputFile: File, packId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outDir = storage.getConvertedDir(packId)
            val trayFile = File(outDir, "tray_${packId}.png")
            val bitmap = when (inputFile.extension.lowercase()) {
                "webm", "mp4" -> {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(inputFile.absolutePath)
                        r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        try { r.release() } catch (_: Exception) {}
                    }
                }
                else -> BitmapFactory.decodeFile(inputFile.absolutePath)
            } ?: throw Exception("Cannot decode tray source")
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            FileOutputStream(trayFile).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            Result.success(trayFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
