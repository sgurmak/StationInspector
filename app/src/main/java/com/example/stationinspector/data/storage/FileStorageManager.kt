package com.example.stationinspector.data.storage

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "FileStorageManager"
    }

    /**
     * Saves a compressed byte array to context.filesDir with a generated UUID filename.
     * @return the absolute path of the saved file or null if saving failed.
     */
    suspend fun savePhoto(byteArray: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { output ->
                output.write(byteArray)
            }

            Log.d(TAG, "Successfully saved photo to: ${file.absolutePath}")
            return@withContext file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error saving photo", e)
            return@withContext null
        }
    }

    /**
     * Deletes a file given its absolute path.
     */
    suspend fun deletePhoto(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Successfully deleted file: $filePath")
            } else {
                Log.e(TAG, "Failed to delete file: $filePath")
            }
            return@withContext deleted
        } else {
            Log.w(TAG, "File does not exist: $filePath")
        }
        return@withContext false
    }

    /**
     * Checks if a file exists.
     */
    suspend fun doesFileExist(filePath: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext File(filePath).exists()
    }

    /**
     * Deletes every stored photo file (the flat `*.jpg` files in filesDir).
     * Photo files are not foreign-key cascaded, so wiping the database leaves
     * them orphaned on disk — this sweep frees that space. Also recovers any
     * pre-existing orphans that earlier delete paths failed to remove.
     *
     * @return the number of files deleted.
     */
    suspend fun clearAllPhotoFiles(): Int = withContext(Dispatchers.IO) {
        val files = context.filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".jpg", ignoreCase = true)
        } ?: return@withContext 0

        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        Log.d(TAG, "Cleared $deleted photo file(s) from storage")
        return@withContext deleted
    }
}