package com.web2app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Base64
import com.web2app.models.AppConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream

// Global app config, loaded once from assets/app_settings.json
var appConfig: AppConfig = AppConfig()

/**
 * Read a JSON file from the assets folder.
 */
fun readJson(context: Context, fileName: String): JSONObject {
    return try {
        val inputStream = context.assets.open(fileName)
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        JSONObject(String(buffer, Charsets.UTF_8))
    } catch (e: Exception) {
        e.printStackTrace()
        JSONObject()
    }
}

/**
 * Utility to decode a Base64 data-URI image string into an Android Drawable.
 * Mirrors Base64ImageUtil from the Compose reference, but returns BitmapDrawable
 * for use with XML Views (e.g. as a BottomNavigationView item icon).
 */
object Base64ImageUtil {
    fun base64ToBitmapDrawable(context: Context, base64String: String): BitmapDrawable? {
        val bitmap = base64ToBitmap(base64String) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    /** Decode a Base64 (optionally data-URI prefixed) string into a Bitmap. */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            if (base64String.isBlank()) return null
            val clean = base64String
                .replace(Regex("^data:image/[^;]*;base64,"), "")
                .trim()
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read an image picked from the gallery (content Uri), downscale it, and
     * encode it as a PNG data-URI Base64 string suitable for storing in prefs.
     */
    fun uriToBase64(context: Context, uri: Uri, maxSize: Int = 512): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null

            val scaled = scaleDown(original, maxSize)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/png;base64,$encoded"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (ratio > 1) {
            maxSize to (maxSize / ratio).toInt()
        } else {
            (maxSize * ratio).toInt() to maxSize
        }
        return Bitmap.createScaledBitmap(bitmap, newW.coerceAtLeast(1), newH.coerceAtLeast(1), true)
    }
}
