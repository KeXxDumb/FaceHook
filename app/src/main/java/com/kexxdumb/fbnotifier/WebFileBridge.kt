package com.kexxdumb.fbnotifier

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class WebFileBridge(private val context: Context) {

    @JavascriptInterface
    fun saveBase64File(base64Data: String, filename: String, mimeType: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(bytes, filename, mimeType)
            } else {
                saveViaLegacyFile(bytes, filename)
            }
            postToast("Imagen guardada en Descargas.")
        } catch (e: Exception) {
            postToast("Error guardando el archivo: ${e.message}")
        }
    }

    @JavascriptInterface
    fun reportError(message: String) {
        postToast("Error al preparar la descarga: $message")
    }

    private fun saveViaMediaStore(bytes: ByteArray, filename: String, mimeType: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo crear el archivo")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
    }

    private fun saveViaLegacyFile(bytes: ByteArray, filename: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { it.write(bytes) }
    }

    private fun postToast(message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
