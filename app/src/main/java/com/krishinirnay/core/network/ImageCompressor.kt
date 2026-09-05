package com.krishinirnay.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downscales + JPEG-compresses a photo before upload — a farmer's
 * full-resolution phone photo on a weak rural connection is a realistic
 * demo-killer otherwise, and an unbounded upload is a cheap DoS vector
 * against the free-tier server. See docs/api-contract.md.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun compress(uri: Uri, maxDimensionPx: Int = 1024, quality: Int = 80): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = maxDimensionPx.toFloat() / maxOf(original.width, original.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true).also {
                original.recycle()
            }
        } else {
            original
        }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return bytes
    }
}
